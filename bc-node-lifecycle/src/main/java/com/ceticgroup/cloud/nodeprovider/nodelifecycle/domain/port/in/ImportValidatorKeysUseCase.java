package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import java.util.List;
import java.util.Objects;

public interface ImportValidatorKeysUseCase {

    List<ValidatorKey> importKeys(ImportValidatorKeysCommand command);

    record ImportValidatorKeysCommand(
            NodeId nodeId, OwnerId requester, List<KeystoreUpload> keystores, String password) {

        public ImportValidatorKeysCommand {
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(keystores, "keystores");
            Objects.requireNonNull(password, "password");
            if (keystores.isEmpty()) {
                throw new IllegalArgumentException("at least one keystore is required");
            }
            if (password.isBlank()) {
                throw new IllegalArgumentException("password must not be blank");
            }
        }
    }

    record KeystoreUpload(String fileName, String content) {

        public KeystoreUpload {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(content, "content");
            if (fileName.isBlank()) {
                throw new IllegalArgumentException("fileName must not be blank");
            }
            if (content.isBlank()) {
                throw new IllegalArgumentException("content must not be blank");
            }
        }
    }
}
