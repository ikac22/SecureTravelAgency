package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import com.zuehlke.securesoftwaredevelopment.repository.HotelSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HotelSnapshotService {
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Path SNAPSHOT_ROOT = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "secure-travel-agency-snapshots"
    );

    private final HotelSnapshotCsvExporter csvExporter;
    private final HotelSnapshotCsvImporter csvImporter;
    private final HotelSnapshotRepository snapshotRepository;

    public HotelSnapshotService(HotelSnapshotCsvExporter csvExporter,
                                HotelSnapshotCsvImporter csvImporter,
                                HotelSnapshotRepository snapshotRepository) {
        this.csvExporter = csvExporter;
        this.csvImporter = csvImporter;
        this.snapshotRepository = snapshotRepository;
    }

    public HotelSnapshot createSnapshot(int hotelId) throws IOException, SQLException, InterruptedException {
        HotelSnapshot baseline = snapshotRepository.findBaselineForHotel(hotelId);
        Long parentSnapshotId = baseline == null ? null : baseline.getId();
        LocalDateTime createdAt = LocalDateTime.now();
        String fileName = snapshotFileName(hotelId, createdAt);
        Path workspace = Files.createTempDirectory("hotel-snapshot-create-");
        Path archivePath = snapshotDirectory(hotelId).resolve(fileName);

        try {
            csvExporter.exportHotelState(hotelId, workspace);
            Files.createDirectories(archivePath.getParent());
            createArchive(workspace, archivePath, HotelSnapshotCsvExporter.SNAPSHOT_FILES);

            long snapshotId = snapshotRepository.create(hotelId, fileName, createdAt, parentSnapshotId);
            return snapshotRepository.findByIdAndHotel(snapshotId, hotelId);
        } catch (IOException | SQLException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(archivePath);
            throw e;
        } finally {
            FileSystemUtils.deleteRecursively(workspace);
        }
    }

    public Path findSnapshotArchive(int hotelId, long snapshotId) {
        HotelSnapshot snapshot = snapshotRepository.findByIdAndHotel(snapshotId, hotelId);
        if (snapshot == null) {
            return null;
        }

        Path directory = snapshotDirectory(hotelId).toAbsolutePath().normalize();
        Path archive = directory.resolve(snapshot.getFileName()).normalize();
        if (!archive.startsWith(directory) || !Files.isRegularFile(archive)) {
            return null;
        }
        return archive;
    }

    public List<String> prepareSelectiveDownload(int hotelId,
                                                 long snapshotId,
                                                 List<String> selectedFiles) {
        if (findSnapshotArchive(hotelId, snapshotId) == null) {
            return null;
        }
        return normalizeSelectedFiles(selectedFiles);
    }

    public byte[] createSelectiveArchive(int hotelId,
                                         long snapshotId,
                                         List<String> selectedFiles) throws IOException, InterruptedException {
        Path sourceArchive = findSnapshotArchive(hotelId, snapshotId);
        if (sourceArchive == null) {
            return null;
        }

        List<String> files = normalizeSelectedFiles(selectedFiles);
        Path workspace = Files.createTempDirectory("hotel-snapshot-selection-");
        Path extracted = workspace.resolve("extracted");
        Path result = workspace.resolve("selected.tar.gz");

        try {
            Files.createDirectories(extracted);
            extractArchive(sourceArchive, extracted, HotelSnapshotCsvExporter.SNAPSHOT_FILES);
            createArchive(extracted, result, files);
            return Files.readAllBytes(result);
        } finally {
            FileSystemUtils.deleteRecursively(workspace);
        }
    }

    public HotelSnapshot rollbackToSnapshot(int hotelId, long snapshotId)
            throws IOException, SQLException, InterruptedException {
        Path sourceArchive = findSnapshotArchive(hotelId, snapshotId);
        if (sourceArchive == null) {
            return null;
        }

        Path workspace = Files.createTempDirectory("hotel-snapshot-rollback-");
        try {
            extractArchive(sourceArchive, workspace, HotelSnapshotCsvExporter.SNAPSHOT_FILES);
            csvImporter.restoreHotelState(hotelId, snapshotId, workspace);
            return snapshotRepository.findByIdAndHotel(snapshotId, hotelId);
        } finally {
            FileSystemUtils.deleteRecursively(workspace);
        }
    }

    Path snapshotPath(HotelSnapshot snapshot) {
        return snapshotDirectory(snapshot.getHotelId()).resolve(snapshot.getFileName());
    }

    Path snapshotDirectory(int hotelId) {
        return SNAPSHOT_ROOT.resolve(String.valueOf(hotelId));
    }

    private List<String> normalizeSelectedFiles(List<String> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one snapshot file");
        }

        LinkedHashSet<String> uniqueFiles = new LinkedHashSet<>(selectedFiles);
        if (uniqueFiles.size() > HotelSnapshotCsvExporter.SNAPSHOT_FILES.size()) {
            throw new IllegalArgumentException("Too many snapshot files selected");
        }

        for (String file : uniqueFiles) {
            if (file == null || file.length() > 64 || !file.endsWith(".csv") ||
                    file.contains("/") || file.contains("\\")) {
                throw new IllegalArgumentException("Invalid snapshot file selection");
            }
        }
        return new ArrayList<>(uniqueFiles);
    }

    private void extractArchive(Path archive,
                                Path directory,
                                List<String> files) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(Arrays.asList(
                "tar",
                "-xzf",
                archive.toString(),
                "-C",
                directory.toString(),
                "--"
        ));
        command.addAll(files);
        runTar(command, "Could not extract snapshot archive");
    }

    private void createArchive(Path directory,
                               Path archivePath,
                               List<String> files) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(Arrays.asList(
                "tar",
                "-czf",
                archivePath.toString(),
                "-C",
                directory.toString()
        ));
        command.addAll(files);
        runTar(command, "Could not create snapshot archive");
    }

    private void runTar(List<String> command, String errorMessage) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(errorMessage + ": " + output);
        }
    }

    private String snapshotFileName(int hotelId, LocalDateTime createdAt) {
        return "hotel-" + hotelId + "-snapshot-" + createdAt.format(FILE_TIME_FORMAT) + "-" +
                UUID.randomUUID().toString().substring(0, 8) + ".tar.gz";
    }
}
