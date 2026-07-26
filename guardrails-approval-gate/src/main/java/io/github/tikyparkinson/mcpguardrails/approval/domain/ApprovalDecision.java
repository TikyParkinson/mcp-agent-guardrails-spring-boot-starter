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
package io.github.tikyparkinson.mcpguardrails.approval.domain;

/**
 * How an approval request ended: approved or not.
 *
 * <p>"Pending" is deliberately absent. It is not an ending but the absence of one, and modelling it
 * here would force every caller to handle a third case that really means "no answer yet".
 */
public sealed interface ApprovalDecision permits Approved, Rejected {}
