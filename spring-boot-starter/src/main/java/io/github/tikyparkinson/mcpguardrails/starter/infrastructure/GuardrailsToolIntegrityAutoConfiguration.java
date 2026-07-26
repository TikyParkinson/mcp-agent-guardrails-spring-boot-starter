/*
 * Copyright 2026 TikyParkinson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain.ToolIntegrityGuardrail;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.mcp.McpToolDefinitionMapper;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.out.catalog.InMemoryToolDefinitionCatalog;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.out.persistence.InMemoryToolBaselineStoreAdapter;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in.VerifyToolIntegrityUseCase;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolDefinitionCatalogPort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.usecase.VerifyToolIntegrityService;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.infrastructure.GuardrailsToolIntegrityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the trust-on-first-use guardrail against tool poisoning.
 *
 * <p>The catalog is filled by {@link GuardrailToolSpecificationPostProcessor} through a {@link
 * ToolDefinitionRegistrar} as each tool is decorated. Without that, the guardrail would find no
 * definition for any tool and fall back to its unknown-definition action on every call — loaded,
 * but deciding on something it never saw.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsToolIntegrityProperties.class)
public class GuardrailsToolIntegrityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ToolBaselineStorePort toolBaselineStorePort() {
    return new InMemoryToolBaselineStoreAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public InMemoryToolDefinitionCatalog toolDefinitionCatalog() {
    return new InMemoryToolDefinitionCatalog();
  }

  /**
   * Feeds the catalog from the tools being decorated. Registered only when this guardrail is
   * active, so a deployment without it records nothing.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.tool-integrity.enabled", matchIfMissing = true)
  public ToolDefinitionRegistrar toolDefinitionRegistrar(InMemoryToolDefinitionCatalog catalog) {
    return tool -> catalog.register(McpToolDefinitionMapper.from(tool));
  }

  @Bean
  @ConditionalOnMissingBean
  public VerifyToolIntegrityUseCase verifyToolIntegrityUseCase(ToolBaselineStorePort store) {
    return new VerifyToolIntegrityService(store);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.tool-integrity.enabled", matchIfMissing = true)
  public ToolIntegrityGuardrail toolIntegrityGuardrail(
      VerifyToolIntegrityUseCase verify,
      ToolDefinitionCatalogPort catalog,
      GuardrailsToolIntegrityProperties properties) {
    return new ToolIntegrityGuardrail(
        verify, catalog, properties.onMismatch(), properties.onUnknownDefinition());
  }
}
