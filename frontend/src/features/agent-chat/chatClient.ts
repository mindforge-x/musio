import { api } from "../../shared/api";
import { AgentEvent, Song } from "../../shared/types";

type AgentRunHandlers = {
  onMessage: (detail: string) => void;
  onToolStart: (detail: string) => void;
  onToolResult: (detail: string) => void;
  onSongCards: (songs: Song[]) => void;
  onError: (detail: string) => void;
  onDone: () => void;
};

export const chatClient = {
  startChat: api.startChat,
  openRunEvents
};

function openRunEvents(runId: string, handlers: AgentRunHandlers): EventSource {
  const source = new EventSource(`/api/chat/runs/${runId}/events`);
  source.onmessage = (event) => {
    handlers.onMessage(event.data);
  };
  source.addEventListener("agent_message_delta", (event) => {
    const agentEvent = parseAgentEvent((event as MessageEvent).data);
    const text = typeof agentEvent?.data?.text === "string" ? agentEvent.data.text : (event as MessageEvent).data;
    handlers.onMessage(text);
  });
  source.addEventListener("tool_start", (event) => {
    handlers.onToolStart(formatToolEvent(parseAgentEvent((event as MessageEvent).data)));
  });
  source.addEventListener("tool_result", (event) => {
    handlers.onToolResult(formatToolEvent(parseAgentEvent((event as MessageEvent).data)));
  });
  source.addEventListener("song_cards", (event) => {
    const agentEvent = parseAgentEvent((event as MessageEvent).data);
    const songs = Array.isArray(agentEvent?.data?.songs) ? (agentEvent.data.songs as Song[]) : [];
    handlers.onSongCards(songs);
  });
  source.addEventListener("agent_error", (event) => {
    const agentEvent = parseAgentEvent((event as MessageEvent).data);
    const detail = typeof agentEvent?.data?.message === "string" ? agentEvent.data.message : (event as MessageEvent).data;
    handlers.onError(detail);
    source.close();
  });
  source.addEventListener("done", () => {
    handlers.onDone();
    source.close();
  });
  source.onerror = () => {
    handlers.onDone();
    source.close();
  };
  return source;
}

function parseAgentEvent(raw: string): AgentEvent | null {
  try {
    return JSON.parse(raw) as AgentEvent;
  } catch {
    return null;
  }
}

function formatToolEvent(event: AgentEvent | null): string {
  if (!event?.data) {
    return "工具事件";
  }

  const tool = typeof event.data.tool === "string" ? event.data.tool : "工具";
  const summary = typeof event.data.summary === "string" ? event.data.summary : "";
  if (summary) {
    return `${tool}: ${summary}`;
  }

  const input = event.data.input ? JSON.stringify(event.data.input) : "";
  return input ? `${tool}: ${input}` : tool;
}
