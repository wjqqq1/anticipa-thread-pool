package com.baomihuahua.anticipa.agent.llm;

import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;

public interface AIClient {
    LLMResponse chat(LLMRequest request);
}
