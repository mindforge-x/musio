import { api } from "../../shared/api";
import { AgentEvent, Song } from "../../shared/types";
import { TraceStep, TraceStepStage, TraceStepStatus, TraceStepVisibility } from "./chatTypes";

type AgentRunHandlers = {
  onMessageDelta: (detail: string, runId?: string) => void;
  onTraceStep: (step: TraceStep) => void;
  onToolStart: (detail: string) => void;
  onToolResult: (detail: string) => void;
  onSongCards: (songs: Song[], runId?: string) => void;
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
    handlers.onMessageDelta(event.data, runId);
  };
  source.addEventListener("agent_message_delta", (event) => {
    const agentEvent = parseAgentEvent((event as MessageEvent).data);
    const text = typeof agentEvent?.data?.text === "string" ? agentEvent.data.text : (event as MessageEvent).data;
    const eventRunId = typeof agentEvent?.data?.runId === "string" ? agentEvent.data.runId : runId;
    handlers.onMessageDelta(text, eventRunId);
  });
  source.addEventListener("trace_step", (event) => {
    const traceStep = parseTraceStep(parseAgentEvent((event as MessageEvent).data), runId);
    if (traceStep) {
      handlers.onTraceStep(traceStep);
    }
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
    const eventRunId = typeof agentEvent?.data?.runId === "string" ? agentEvent.data.runId : runId;
    handlers.onSongCards(songs, eventRunId);
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
    handlers.onError("SSE 连接已中断。");
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

function parseTraceStep(event: AgentEvent | null, fallbackRunId: string): TraceStep | null {
  const data = event?.data;
  if (!data) {
    return null;
  }

  const stepId = typeof data.stepId === "string" ? data.stepId : "";
  const title = typeof data.title === "string" ? data.title : "";
  const stage = typeof data.stage === "string" && isTraceStage(data.stage) ? data.stage : null;
  const status = typeof data.status === "string" && isTraceStatus(data.status) ? data.status : null;
  const visibility = typeof data.visibility === "string" && isTraceVisibility(data.visibility) ? data.visibility : null;
  if (!stepId || !title || !stage || !status || !visibility) {
    return null;
  }

  return {
    runId: typeof data.runId === "string" ? data.runId : fallbackRunId,
    stepId,
    stage,
    status,
    visibility,
    title,
    summary: typeof data.summary === "string" ? data.summary : undefined,
    safeData: isRecord(data.safeData) ? data.safeData : undefined
  };
}

function isTraceStage(value: string): value is TraceStepStage {
  return ["intent", "context", "tool", "compose", "render", "error"].includes(value);
}

function isTraceStatus(value: string): value is TraceStepStatus {
  return ["pending", "running", "done", "error", "skipped"].includes(value);
}

function isTraceVisibility(value: string): value is TraceStepVisibility {
  return ["user", "debug"].includes(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
