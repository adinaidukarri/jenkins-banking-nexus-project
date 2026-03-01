package com.banking;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * BankingServlet — The Web Front Door of the Bank.
 *
 * Analogy:
 *   This is the online banking portal's backend.
 *   Customers visit the website (index.html), fill a form,
 *   and this servlet processes their request using
 *   AccountService (the rule book from Nexus).
 *
 * Maps to URL: /banking-app/api/*
 *   /api/interest  → calculateInterest
 *   /api/loan      → isEligibleForLoan
 *   /api/tier      → getAccountTier
 */
@WebServlet("/api/*")
public class BankingServlet extends HttpServlet {

    private AccountService service;

    @Override
    public void init() {
        // Load the "rule book" from banking-core (fetched from Nexus at build time)
        service = new AccountService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo(); // e.g. /interest, /loan, /tier
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        // Allow same-origin AJAX calls
        resp.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = resp.getWriter();

        try {
            if ("/interest".equals(action)) {
                double principal = Double.parseDouble(req.getParameter("principal"));
                double rate      = Double.parseDouble(req.getParameter("rate"));
                int    years     = Integer.parseInt(req.getParameter("years"));
                double interest  = service.calculateInterest(principal, rate, years);
                double total     = principal + interest;
                out.printf("{\"interest\":%.2f,\"total\":%.2f}", interest, total);

            } else if ("/loan".equals(action)) {
                double balance  = Double.parseDouble(req.getParameter("balance"));
                boolean eligible = service.isEligibleForLoan(balance);
                out.printf("{\"eligible\":%b}", eligible);

            } else if ("/tier".equals(action)) {
                double balance = Double.parseDouble(req.getParameter("balance"));
                String tier    = service.getAccountTier(balance);
                out.printf("{\"tier\":\"%s\"}", tier);

            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\":\"Unknown endpoint\"}");
            }

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Invalid number input. Please enter valid numeric values.\"}");
        }
    }

    // Support preflight OPTIONS (CORS)
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
