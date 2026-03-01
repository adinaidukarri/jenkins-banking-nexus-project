pipeline {

    agent any

    tools {
        maven 'maven'
    }

    environment {
        // ── Nexus ──────────────────────────────────────────────────────
        NEXUS_CREDENTIALS_ID  = 'nexus'
        NEXUS_URL             = 'http://172.31.34.152:8081'

        // ── Tomcat ─────────────────────────────────────────────────────
        TOMCAT_CREDENTIALS_ID = 'tomcat'                        // Jenkins credential ID for Tomcat
        TOMCAT_URL            = 'http://172.31.35.88:8080'  // Replace with your Tomcat EC2 IP/hostname
        APP_CONTEXT_PATH      = '/banking-app'                  // URL context: http://host:8080/banking-app

        // ── SonarQube ──────────────────────────────────────────────────
        SONAR_TOKEN           = credentials('sonarqube')        // Jenkins secret text credential ID: 'sonarqube'

        // ── Slack ──────────────────────────────────────────────────────
        SLACK_WEBHOOK_URL     = credentials('slack-webhook')    // Jenkins secret text credential ID: 'slack-webhook'
    }

    stages {

        // ════════════════════════════════════════════════════════════════
        // Stage 1 — Checkout
        // ════════════════════════════════════════════════════════════════
        stage('Checkout') {
            steps {
                echo '📦 Stage 1: Checking out source code from GitHub...'
                checkout scm
                echo '✅ Source code checked out successfully.'
                sh 'ls -la'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 2 — Build banking-core (shared business logic JAR)
        // ════════════════════════════════════════════════════════════════
        stage('Build Core') {
            steps {
                echo '🔨 Stage 2: Building banking-core module...'
                sh 'mvn -pl banking-core --also-make clean install -DskipTests'
                echo '✅ banking-core built and installed to local .m2 cache.'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 3 — Deploy banking-core JAR + parent POM to Nexus
        // ════════════════════════════════════════════════════════════════
        stage('Deploy Core to Nexus') {
            steps {
                echo '🚚 Stage 3: Deploying parent POM + banking-core JAR to Nexus...'
                withCredentials([usernamePassword(
                    credentialsId: "${NEXUS_CREDENTIALS_ID}",
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh """
                        # Step 3a: Deploy parent POM first (-N = non-recursive, root POM only)
                        # Analogy: Ship the bank's master policy doc to Nexus warehouse first
                        mvn deploy -N -DskipTests \
                            -Dusername=${NEXUS_USER} \
                            -Dpassword=${NEXUS_PASS}

                        # Step 3b: Now deploy banking-core JAR
                        mvn -pl banking-core deploy -DskipTests \
                            -Dusername=${NEXUS_USER} \
                            -Dpassword=${NEXUS_PASS}
                    """
                }
                echo '✅ Parent POM + banking-core-1.0-SNAPSHOT.jar deployed to Nexus!'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 4 — Build banking-api WAR (pulls banking-core from Nexus)
        // ════════════════════════════════════════════════════════════════
        stage('Build API') {
            steps {
                echo '🏗️ Stage 4: Building banking-api (pulls banking-core from Nexus)...'
                sh 'mvn -pl banking-api clean package -DskipTests'
                echo '✅ banking-api built successfully using banking-core from Nexus.'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 5 — Unit Tests (JUnit 5)
        // ════════════════════════════════════════════════════════════════
        stage('Test') {
            steps {
                echo '🧪 Stage 5: Running unit tests on banking-api...'
                sh 'mvn -pl banking-api test'
                echo '✅ All tests passed!'
            }
            post {
                always {
                    junit 'banking-api/target/surefire-reports/*.xml'
                }
                failure {
                    echo '❌ Tests failed! Fix before proceeding.'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 6 — SonarQube Code Analysis
        //   Scans ALL modules (banking-core + banking-api) for:
        //     • Bugs & code smells
        //     • Security vulnerabilities
        //     • Code coverage (from JUnit results)
        //
        //   ⚠️  IMPORTANT: sonar:sonar MUST run from the ROOT POM (no -pl flag).
        //   The SonarQube Maven plugin requires a "top level project" context
        //   to understand the full multi-module structure. Using -pl causes:
        //   "Maven session does not declare a top level project" error.
        //
        //   Requires: SonarQube server configured in Jenkins as 'sonarqube'
        // ════════════════════════════════════════════════════════════════
        stage('SonarQube Analysis') {
            steps {
                echo '🔍 Stage 6: Running SonarQube code analysis (all modules)...'
                withSonarQubeEnv('sonarqube') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=banking-app \
                            -Dsonar.projectName='Banking Application' \
                            -Dsonar.token=${SONAR_TOKEN}
                    """
                }
                echo '✅ SonarQube analysis submitted successfully.'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 7 — SonarQube Quality Gate
        //   Waits for SonarQube to finish processing the report and
        //   checks if the project passes the Quality Gate.
        //   Pipeline ABORTS if Quality Gate fails (bugs/vulnerabilities
        //   exceed the threshold set in SonarQube).
        // ════════════════════════════════════════════════════════════════
        stage('Quality Gate') {
            steps {
                echo '🚦 Stage 7: Waiting for SonarQube Quality Gate result...'
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo '✅ Quality Gate passed! Code meets the required standards.'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 8 — Publish banking-api WAR to Nexus
        // ════════════════════════════════════════════════════════════════
        stage('Publish API to Nexus') {
            steps {
                echo '📤 Stage 8: Publishing banking-api WAR artifact to Nexus...'
                withCredentials([usernamePassword(
                    credentialsId: "${NEXUS_CREDENTIALS_ID}",
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh """
                        mvn -pl banking-api deploy -DskipTests \
                            -Dusername=${NEXUS_USER} \
                            -Dpassword=${NEXUS_PASS}
                    """
                }
                echo '✅ banking-app.war published to Nexus!'
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Stage 9 — Deploy WAR to Tomcat Server
        //   Uses Tomcat Manager REST API (curl) to hot-deploy the WAR.
        //   Requires: Tomcat Manager user with role 'manager-script'
        // ════════════════════════════════════════════════════════════════
        stage('Deploy to Tomcat') {
            steps {
                echo '🚀 Stage 9: Deploying WAR to Tomcat Server...'
                withCredentials([usernamePassword(
                    credentialsId: "${TOMCAT_CREDENTIALS_ID}",
                    usernameVariable: 'TOMCAT_USER',
                    passwordVariable: 'TOMCAT_PASS'
                )]) {
                    sh """
                        # Undeploy old version first (ignore error if not deployed yet)
                        curl -s -u ${TOMCAT_USER}:${TOMCAT_PASS} \
                            "${TOMCAT_URL}/manager/text/undeploy?path=${APP_CONTEXT_PATH}" || true

                        # Deploy new WAR via Tomcat Manager REST API
                        curl -v -f -u ${TOMCAT_USER}:${TOMCAT_PASS} \
                            -T banking-api/target/banking-app.war \
                            "${TOMCAT_URL}/manager/text/deploy?path=${APP_CONTEXT_PATH}&update=true"
                    """
                }
                echo "✅ WAR deployed! Open: ${TOMCAT_URL}${APP_CONTEXT_PATH}"
            }
        }

    }

    // ══════════════════════════════════════════════════════════════════════
    // Post — Slack Notifications
    //   Sends build result to Slack channel via Incoming Webhook.
    //   Credential 'slack-webhook' must be a Jenkins Secret Text credential
    //   containing the full Slack webhook URL.
    // ══════════════════════════════════════════════════════════════════════
    post {

        success {
            sh """
                curl -X POST -H 'Content-type: application/json' \
                --data '{"text": ":white_check_mark: *BUILD SUCCESS* \\nJob: *${JOB_NAME}* \\nBuild: *#${BUILD_NUMBER}* \\nURL: ${BUILD_URL}"}' \
                ${SLACK_WEBHOOK_URL}
            """
        }

        failure {
            sh """
                curl -X POST -H 'Content-type: application/json' \
                --data '{"text": ":x: *BUILD FAILED* \\nJob: *${JOB_NAME}* \\nBuild: *#${BUILD_NUMBER}* \\nURL: ${BUILD_URL}"}' \
                ${SLACK_WEBHOOK_URL}
            """
        }

        always {
            sh """
                curl -X POST -H 'Content-type: application/json' \
                --data '{"text": ":bell: *BUILD COMPLETED* \\nJob: *${JOB_NAME}* \\nStatus: *${currentBuild.currentResult}* \\nBuild: *#${BUILD_NUMBER}* \\nURL: ${BUILD_URL}"}' \
                ${SLACK_WEBHOOK_URL}
            """
            echo '🧹 Cleaning workspace...'
            cleanWs()
        }

    }
}
