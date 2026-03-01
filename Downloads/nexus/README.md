# Jenkins + Nexus + Maven: End-to-End CI Pipeline Guide

## Why Are We Doing This?

Imagine a large bank with multiple departments:

- The **Core Banking Team** writes the interest calculation rules, loan eligibility logic, and account management code.
- The **API Team** builds the customer-facing services that *use* those rules.

Without a proper system, the API Team would have to either:
1. Copy-paste the Core team's code into their own project (dangerous — now two copies exist, they go out of sync), or
2. Wait for the Core team to send them a ZIP file every time something changes (slow, error-prone).

**Nexus solves this.** It acts as a **central warehouse** — the Core team ships their packaged JAR to Nexus, and the API team pulls it from there. Everyone always gets the same, tested, versioned artifact. No copy-pasting. No ZIP files.

**Jenkins automates the entire flow.** Every time code is pushed to GitHub, Jenkins:
1. Builds the Core library and ships it to the Nexus warehouse
2. Builds the API service, pulling Core from Nexus (not from source)
3. Runs tests to verify everything works together
4. Ships the final API artifact back to Nexus — ready for deployment

This is the **real-world DevOps workflow** used by every serious engineering team.

---

## Architecture

```
Developer pushes code to GitHub
           │
           ▼
    Jenkins picks it up
           │
    ┌──────┴──────────────────────────────────┐
    │                                          │
    ▼                                          │
Build banking-core                             │
    │                                          │
    ▼                                          │
Deploy banking-core JAR ──────────► Nexus      │
    + Parent POM               (Warehouse)     │
                                    │          │
    ┌───────────────────────────────┘          │
    │  banking-api pulls banking-core from Nexus│
    ▼                                          │
Build banking-api                             │
    │                                          │
    ▼                                          │
Run Tests (JUnit 5)                            │
    │                                          │
    ▼                                          │
Deploy banking-api JAR ───────────► Nexus      │
                               (Warehouse) ────┘
```

---

## Project Structure

```
banking-app/                          ← Parent project (the "Head Office")
├── pom.xml                           ← Parent POM — shared config for all modules
├── Jenkinsfile                       ← CI pipeline definition (6 stages)
├── settings.xml                      ← Maven auth config for Nexus (on Jenkins server)
├── banking-core/                     ← Shared library (the "Rule Book")
│   ├── pom.xml
│   └── src/main/java/com/banking/
│       └── AccountService.java       ← Business logic: interest calc, loan eligibility
└── banking-api/                      ← Customer-facing layer (the "Teller Counter")
    ├── pom.xml
    └── src/main/java/com/banking/
    │   └── BankController.java       ← Uses AccountService from Nexus JAR
    └── src/test/java/com/banking/
        └── BankControllerTest.java   ← JUnit 5 tests (8 test cases)
```

---

## Tools & Versions

| Tool | Version | Role |
|------|---------|------|
| Java | OpenJDK 21 (Corretto) | Language |
| Maven | 3.x | Build tool |
| Jenkins | Latest | CI/CD automation |
| Nexus Repository Manager | 3.x | Artifact warehouse |
| GitHub | — | Source code storage |

---

## Step 1 — Set Up Nexus Repositories

Nexus needs two separate repositories — one for work-in-progress builds (SNAPSHOTs) and one for final, immutable releases.

**Analogy:** Think of SNAPSHOT as a daily newspaper — same name, updated every day. A RELEASE is a published book — once printed with an ISBN, the content never changes.

### Create `banking-snapshots` repository

1. Login to Nexus → **Settings (gear icon)** → **Repositories** → **Create repository**
2. Select recipe: `maven2 (hosted)`
3. Fill in:
   - **Name:** `banking-snapshots`
   - **Version Policy:** `Snapshot` ← **critical — must be Snapshot, not Release**
   - **Deployment Policy:** `Allow Redeploy` ← allows Jenkins to overwrite on every build
4. Click **Save**

### Create `banking-releases` repository

1. Repeat the above steps with:
   - **Name:** `banking-releases`
   - **Version Policy:** `Release`
   - **Deployment Policy:** `Disable Redeploy` ← immutable, once published never changed
2. Click **Save**

> ⚠️ **Common mistake:** Setting Version Policy to `Release` on the snapshots repo causes a `400` error: *"Repository version policy: RELEASE does not allow version: 1.0-SNAPSHOT"*. Always match the policy to the version type.

---

## Step 2 — Set Up Jenkins

### Install Maven on Jenkins server

```bash
sudo apt update
sudo apt install maven -y
mvn -version
```

### Configure Maven tool in Jenkins

Go to **Manage Jenkins → Tools → Maven installations → Add Maven**

- **Name:** `maven` ← this exact name must match the Jenkinsfile `tools { maven 'maven' }`
- **Install automatically** or provide the path


### Add Nexus credentials in Jenkins

Go to **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

| Field | Value |
|-------|-------|
| Kind | `Username with password` |
| Username | Your Nexus admin username |
| Password | Your Nexus admin password |
| ID | `nexus` ← this must match `NEXUS_CREDENTIALS_ID` in the Jenkinsfile |
| Description | `Nexus Repository Credentials` |

---

## Step 3 — Create the Maven Project

### Parent POM (`banking-app/pom.xml`)

The parent POM is the "Head Office policy document" — all child modules inherit settings from it. The critical section is `<distributionManagement>`, which tells Maven **where to ship artifacts** when you run `mvn deploy`.

```xml
<distributionManagement>
  <snapshotRepository>
    <id>nexus-snapshots</id>
    <url>http://YOUR_NEXUS_IP:8081/repository/banking-snapshots/</url>
  </snapshotRepository>
  <repository>
    <id>nexus-releases</id>
    <url>http://YOUR_NEXUS_IP:8081/repository/banking-releases/</url>
  </repository>
</distributionManagement>
```

> The `<id>` values (`nexus-snapshots`, `nexus-releases`) must match the `<server>` IDs in `settings.xml`. Maven uses these IDs to look up the username/password for authentication.

### banking-core (`banking-core/pom.xml`)

Points to parent, packages as `jar`. Contains `AccountService.java` with business logic.

### banking-api (`banking-api/pom.xml`)

Points to parent, packages as `jar`. Declares `banking-core` as a dependency — Maven will resolve this from Nexus (not from source).

```xml
<dependency>
  <groupId>com.banking</groupId>
  <artifactId>banking-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Step 4 — Configure `settings.xml` on Jenkins Server

`settings.xml` is Maven's authentication config. It tells Maven: *"when you need to talk to Nexus, use these credentials."*

**Analogy:** It's the ID badge the Jenkins delivery driver shows at the Nexus warehouse gate.

### Place the file on the Jenkins server

```bash
sudo mkdir -p /var/lib/jenkins/.m2
sudo nano /var/lib/jenkins/.m2/settings.xml
```

### Contents of `settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>YOUR_NEXUS_USERNAME</username>
      <password>YOUR_NEXUS_PASSWORD</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>YOUR_NEXUS_USERNAME</username>
      <password>YOUR_NEXUS_PASSWORD</password>
    </server>
  </servers>
</settings>
```

```bash
# Set correct ownership so Jenkins can read it
sudo chown -R jenkins:jenkins /var/lib/jenkins/.m2
```

> ⚠️ **Important:** The `<id>` values here must exactly match the `<id>` values in `<distributionManagement>` in your parent `pom.xml`. Maven uses this ID as the link between "where to deploy" and "which credentials to use."

> ⚠️ **Does this affect other Jenkins projects?** Placing `settings.xml` at `/var/lib/jenkins/.m2/settings.xml` applies to all Jenkins builds. Other projects will use Nexus as a download mirror, which is harmless if Nexus has a proxy to Maven Central. To keep it isolated to this project only, reference settings in the Jenkinsfile using `mvn deploy --settings ./settings.xml`.

---

## Step 5 — Write the Jenkinsfile

The Jenkinsfile is the **assembly line blueprint**. It lives in the root of the repo so Jenkins can read it directly from GitHub.

### Pipeline stages explained

```
Stage 1: Checkout       → Pull code from GitHub into Jenkins workspace
Stage 2: Build Core     → mvn install on banking-core (JAR goes to local .m2 cache)
Stage 3: Deploy Core    → Push parent POM + banking-core JAR to Nexus
Stage 4: Build API      → mvn package on banking-api (pulls banking-core from Nexus)
Stage 5: Test           → mvn test — runs 8 JUnit 5 tests
Stage 6: Publish API    → Push banking-api JAR to Nexus
```

### Why deploy the parent POM separately in Stage 3?

When `banking-api` pulls `banking-core` from Nexus, Maven also needs the **parent POM** (`banking-app`) from Nexus to understand the full dependency tree. If only the core JAR is deployed, Stage 4 fails with:

```
Could not find artifact com.banking:banking-app:pom:1.0-SNAPSHOT
```

The fix is to run `mvn deploy -N` first (`-N` = non-recursive = root POM only), then deploy banking-core:

```bash
# Deploy parent POM to Nexus first
mvn deploy -N -DskipTests

# Then deploy banking-core JAR
mvn -pl banking-core deploy -DskipTests
```

### `withCredentials` — why not hardcode the password?

```groovy
withCredentials([usernamePassword(
    credentialsId: 'nexus',
    usernameVariable: 'NEXUS_USER',
    passwordVariable: 'NEXUS_PASS'
)]) {
    sh 'mvn deploy -Dusername=${NEXUS_USER} -Dpassword=${NEXUS_PASS}'
}
```

Jenkins injects the credentials at runtime from its secure credentials store. The password never appears in the Jenkinsfile, GitHub, or build logs — it's masked as `****`.

**Analogy:** Like a bank teller's PIN — they type it at a keypad, it's never written on the counter.

---

## Step 6 — Create the Jenkins Pipeline Job

1. Jenkins dashboard → **New Item**
2. Name: `banking-app-pipeline` → Select **Pipeline** → **OK**
3. Scroll to **Pipeline** section:
   - **Definition:** `Pipeline script from SCM` ← must NOT be "Pipeline script"
   - **SCM:** `Git`
   - **Repository URL:** `https://github.com/YOUR_USERNAME/jenkins-banking-nexus-project.git`
   - **Branch:** `*/main`
   - **Script Path:** `Jenkinsfile`
4. **Save** → **Build Now**

> ⚠️ **Common mistake:** Using "Pipeline script" mode and pasting the Jenkinsfile directly into Jenkins UI. The `checkout scm` command only works with "Pipeline script from SCM" mode, where Jenkins knows which Git repo it came from.

---

## Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `Tool type "maven" does not have an install of "Maven-3.9"` | Tool name in Jenkinsfile doesn't match Jenkins config | Use exact name from Manage Jenkins → Tools |
| `checkout scm is only available when using Multibranch Pipeline` | Job is set to "Pipeline script" mode | Change to "Pipeline script from SCM" in job config |
| `status code: 400 — RELEASE does not allow version 1.0-SNAPSHOT` | Nexus repo Version Policy set to Release instead of Snapshot | Edit Nexus repo → change Version Policy to Snapshot |
| `Could not find artifact com.banking:banking-app:pom:1.0-SNAPSHOT` | Parent POM was never deployed to Nexus | Add `mvn deploy -N` before deploying banking-core |
| `401 Unauthorized` | Wrong credentials ID in Jenkinsfile or wrong password in settings.xml | Verify credential ID matches exactly in Jenkins + settings.xml |

---

## Verifying Success in Nexus

After a successful pipeline run, verify in Nexus:

1. Go to `http://YOUR_NEXUS_IP:8081`
2. **Browse** → `banking-snapshots`
3. You should see:
   - `com/banking/banking-app/1.0-SNAPSHOT/` ← parent POM
   - `com/banking/banking-core/1.0-SNAPSHOT/` ← core JAR
   - `com/banking/banking-api/1.0-SNAPSHOT/` ← api JAR

---

## Key Concepts Summary

| Concept | Analogy | Purpose |
|---------|---------|---------|
| Nexus | Supermarket warehouse | Central store for all artifacts |
| JAR/WAR (artifact) | Baked cake (not raw ingredients) | Compiled, versioned, deployable unit |
| SNAPSHOT version | Daily newspaper | Work in progress, gets overwritten each build |
| RELEASE version | Published book (with ISBN) | Final, immutable, never overwritten |
| Parent POM | Bank's head office policy | Shared config inherited by all modules |
| `mvn deploy` | Courier shipping a parcel | Uploads artifact to Nexus |
| `settings.xml` | ID badge at warehouse gate | Maven credentials for Nexus auth |
| `distributionManagement` | Shipping label on a parcel | Tells Maven where to deliver artifacts |
| `withCredentials` | Bank teller's PIN keypad | Secure credential injection at runtime |
| `-N` flag (non-recursive) | Signing only the cover page | Processes only root POM, not submodules |

---

## GitHub Repository

[https://github.com/avizway1/jenkins-banking-nexus-project](https://github.com/avizway1/jenkins-banking-nexus-project)
