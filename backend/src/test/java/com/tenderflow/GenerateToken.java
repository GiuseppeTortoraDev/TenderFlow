package com.tenderflow;

import io.smallrye.jwt.build.Jwt;
import java.util.Set;

public class GenerateToken {

    public static String generateBuyerToken() {
        return Jwt.issuer("https://tenderflow.it/issuer")
                .upn("buyer@tenderflow.it")
                .groups(Set.of("BUYER"))
                .claim("fullName", "Buyer Test Srl")
                .expiresIn(3600)
                .sign();
    }

    public static String generateSupplierToken() {
        return Jwt.issuer("https://tenderflow.it/issuer")
                .upn("supplier@tenderflow.it")
                .groups(Set.of("SUPPLIER"))
                .claim("fullName", "Supplier Test SpA")
                .expiresIn(3600)
                .sign();
    }

    public static void main(String[] args) {
        System.out.println("--- BUYER TOKEN ---");
        System.out.println(generateBuyerToken());
        System.out.println("\n--- SUPPLIER TOKEN ---");
        System.out.println(generateSupplierToken());
    }
}