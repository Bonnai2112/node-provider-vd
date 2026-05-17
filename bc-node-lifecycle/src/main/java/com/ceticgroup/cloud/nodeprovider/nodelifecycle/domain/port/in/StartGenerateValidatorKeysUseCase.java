package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysCommand;

public interface StartGenerateValidatorKeysUseCase {

    KeyGenerationJobId start(GenerateValidatorKeysCommand command);
}
