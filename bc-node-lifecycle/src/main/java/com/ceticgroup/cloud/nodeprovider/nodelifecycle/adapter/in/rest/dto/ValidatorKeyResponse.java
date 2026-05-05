package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ValidatorKeyResponse(UUID id, String pubkey, Instant importedAt) {}
