/**
 * AI agent boundary. Uses LangChain4j {@code @RegisterAiService} with plain
 * {@code @SystemMessage}/{@code @UserMessage} rather than function-calling because:
 * the snake decision is a single token (UP/DOWN/LEFT/RIGHT) requiring no structured
 * tool call, function-calling adds latency via an extra round-trip, and the
 * constrained output space makes plain text extraction reliable.
 */
package dev.denisarruda.gemmasnakeai.agent;
