package com.tenderflow.service;

import com.tenderflow.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@ApplicationScoped
public class DocumentStorageService {

    @ConfigProperty(name = "tenderflow.storage.dir", defaultValue = "./uploads/tenders")
    String storageDir;

    public String storePdf(Long tenderId, String originalFileName, InputStream inputStream) {
        if (inputStream == null) {
            throw new BusinessException("Nessun file inviato");
        }

        String safeOriginalName = (originalFileName == null || originalFileName.isBlank())
                ? "capitolato.pdf"
                : Paths.get(originalFileName).getFileName().toString();

        if (!safeOriginalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("Formato non valido: sono consentiti solo documenti PDF (.pdf)");
        }

        try {
            byte[] fileBytes = inputStream.readAllBytes();
            if (fileBytes.length == 0) {
                throw new BusinessException("Il file PDF inviato è vuoto");
            }
            if (fileBytes.length > 10 * 1024 * 1024) {
                throw new BusinessException("Il file PDF supera la dimensione massima consentita di 10 MB");
            }
            if (fileBytes.length < 5 || !new String(fileBytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new BusinessException("Il contenuto del file non corrisponde a un documento PDF valido");
            }

            Path directory = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);

            String storedName = "tender-" + tenderId + "-" + UUID.randomUUID() + ".pdf";
            Path targetPath = directory.resolve(storedName).normalize();

            if (!targetPath.startsWith(directory)) {
                throw new BusinessException("Percorso file non valido");
            }

            Files.write(targetPath, fileBytes);
            return storedName;
        } catch (IOException e) {
            throw new BusinessException("Errore durante il salvataggio del documento PDF", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public Path resolveDocumentPath(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new BusinessException("Nessun capitolato allegato a questa gara", Response.Status.NOT_FOUND);
        }

        Path directory = Paths.get(storageDir).toAbsolutePath().normalize();
        Path filePath = directory.resolve(storedName).normalize();

        if (!filePath.startsWith(directory) || !Files.exists(filePath)) {
            throw new BusinessException("Documento PDF non trovato sullo storage", Response.Status.NOT_FOUND);
        }

        return filePath;
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Path directory = Paths.get(storageDir).toAbsolutePath().normalize();
            Path filePath = directory.resolve(storedName).normalize();
            if (filePath.startsWith(directory)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
        }
    }
}