package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.GenerateValidatorKeysAcceptedResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.GenerateValidatorKeysRequest;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.GenerateValidatorKeysResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.KeyGenerationJobStatusResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.ValidatorKeyResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.DownloadValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase.ImportValidatorKeysCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase.KeystoreUpload;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.PollKeyGenerationJobUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.StartGenerateValidatorKeysUseCase;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/nodes/{id}/validator-keys")
class ValidatorKeyController {

    private static final String OWNER_HEADER = "X-Owner-Id";

    private final ListValidatorKeysUseCase listUseCase;
    private final ImportValidatorKeysUseCase importUseCase;
    private final StartGenerateValidatorKeysUseCase startGenerateUseCase;
    private final PollKeyGenerationJobUseCase pollJobUseCase;
    private final DownloadValidatorKeysUseCase downloadUseCase;

    ValidatorKeyController(
            ListValidatorKeysUseCase listUseCase,
            ImportValidatorKeysUseCase importUseCase,
            StartGenerateValidatorKeysUseCase startGenerateUseCase,
            PollKeyGenerationJobUseCase pollJobUseCase,
            DownloadValidatorKeysUseCase downloadUseCase) {
        this.listUseCase = listUseCase;
        this.importUseCase = importUseCase;
        this.startGenerateUseCase = startGenerateUseCase;
        this.pollJobUseCase = pollJobUseCase;
        this.downloadUseCase = downloadUseCase;
    }

    @GetMapping
    List<ValidatorKeyResponse> list(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        return listUseCase.listByNode(new NodeId(id), new OwnerId(ownerId)).stream()
                .map(ValidatorKeyController::toResponse)
                .toList();
    }

    @GetMapping("/download")
    ResponseEntity<byte[]> download(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        byte[] zip = downloadUseCase.downloadKeystores(new NodeId(id), new OwnerId(ownerId));
        String filename = "keystores-" + id + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(zip);
    }

    @GetMapping("/deposit-data")
    ResponseEntity<byte[]> depositData(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        byte[] json = downloadUseCase.downloadDepositData(new NodeId(id), new OwnerId(ownerId));
        String filename = "deposit_data-" + id + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(json);
    }

    @GetMapping("/{pubkey}/keystore")
    ResponseEntity<byte[]> keystoreFor(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @PathVariable String pubkey) {
        byte[] json =
                downloadUseCase.downloadKeystoreFor(new NodeId(id), new OwnerId(ownerId), pubkey);
        String filename = "keystore-" + shortPubkey(pubkey) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(json);
    }

    @GetMapping("/{pubkey}/deposit-data")
    ResponseEntity<byte[]> depositDataFor(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @PathVariable String pubkey) {
        byte[] json =
                downloadUseCase.downloadDepositDataFor(
                        new NodeId(id), new OwnerId(ownerId), pubkey);
        String filename = "deposit_data-" + shortPubkey(pubkey) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(json);
    }

    private static String shortPubkey(String pubkey) {
        String s =
                pubkey.startsWith("0x") || pubkey.startsWith("0X") ? pubkey.substring(2) : pubkey;
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    @PostMapping("/generate")
    ResponseEntity<GenerateValidatorKeysAcceptedResponse> generate(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody
                    GenerateValidatorKeysRequest request) {
        GenerateValidatorKeysCommand command =
                new GenerateValidatorKeysCommand(
                        new NodeId(id),
                        new OwnerId(ownerId),
                        request.count(),
                        request.withdrawalAddress());
        KeyGenerationJobId jobId = startGenerateUseCase.start(command);
        return ResponseEntity.accepted()
                .body(new GenerateValidatorKeysAcceptedResponse(jobId.value()));
    }

    @GetMapping("/generate-jobs/{jobId}")
    ResponseEntity<KeyGenerationJobStatusResponse> pollGenerateJob(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @PathVariable UUID jobId) {
        // {id} is part of the URL for symmetry with the rest of the resource hierarchy, but the
        // registry alone is the source of truth for which job belongs to which owner; the node id
        // is not cross-checked.
        return pollJobUseCase
                .poll(new KeyGenerationJobId(jobId), new OwnerId(ownerId))
                .map(ValidatorKeyController::toJobStatusResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static KeyGenerationJobStatusResponse toJobStatusResponse(KeyGenerationJobState state) {
        return switch (state) {
            case KeyGenerationJobState.Running ignored -> KeyGenerationJobStatusResponse.running();
            case KeyGenerationJobState.Succeeded s ->
                    KeyGenerationJobStatusResponse.succeeded(
                            new GenerateValidatorKeysResponse(
                                    s.result().mnemonic(),
                                    s.result().password(),
                                    s.result().keys().stream()
                                            .map(ValidatorKeyController::toResponse)
                                            .toList()));
            case KeyGenerationJobState.Failed f ->
                    KeyGenerationJobStatusResponse.failed(f.message());
        };
    }

    @PostMapping("/import")
    List<ValidatorKeyResponse> importKeys(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @RequestParam("keystores") MultipartFile[] keystoreFiles,
            @RequestParam("password") String password) {
        if (keystoreFiles == null || keystoreFiles.length == 0) {
            throw new IllegalArgumentException("at least one keystore file is required");
        }
        List<KeystoreUpload> uploads = readUploads(keystoreFiles);
        ImportValidatorKeysCommand command =
                new ImportValidatorKeysCommand(
                        new NodeId(id), new OwnerId(ownerId), uploads, password);
        return importUseCase.importKeys(command).stream()
                .map(ValidatorKeyController::toResponse)
                .toList();
    }

    private static List<KeystoreUpload> readUploads(MultipartFile[] files) {
        return java.util.Arrays.stream(files).map(ValidatorKeyController::readUpload).toList();
    }

    private static KeystoreUpload readUpload(MultipartFile file) {
        try {
            String name =
                    file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                            ? "keystore-" + UUID.randomUUID() + ".json"
                            : file.getOriginalFilename();
            return new KeystoreUpload(name, new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("could not read uploaded keystore", e);
        }
    }

    private static ValidatorKeyResponse toResponse(ValidatorKey key) {
        return new ValidatorKeyResponse(key.id(), key.pubkey(), key.importedAt());
    }
}
