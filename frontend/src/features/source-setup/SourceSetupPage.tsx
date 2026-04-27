import { useEffect, useState } from "react";
import { EventLog, LoginStartResult, LoginStatus } from "../../shared/types";
import { providerSetupClient } from "./providerSetupClient";
import { ProviderLoginCard } from "./ProviderLoginCard";

type SourceSetupPageProps = {
  busy: boolean;
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
};

const QQ_PROVIDER = "qqmusic";
const TERMINAL_LOGIN_STATES = new Set(["DONE", "EXPIRED", "FAILED"]);

export function SourceSetupPage({ busy, onBusyChange, onEvent }: SourceSetupPageProps) {
  const [login, setLogin] = useState<LoginStartResult | null>(null);
  const [loginStatus, setLoginStatus] = useState<LoginStatus | null>(null);

  useEffect(() => {
    if (!login?.sessionId) {
      return;
    }

    if (loginStatus && TERMINAL_LOGIN_STATES.has(loginStatus.state)) {
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const result = await providerSetupClient.loginStatus(QQ_PROVIDER, login.sessionId);
        if (cancelled) {
          return;
        }
        setLoginStatus((previous) => {
          if (previous?.state !== result.state) {
            onEvent({ id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` });
          }
          return result;
        });
      } catch (error) {
        if (!cancelled) {
          onEvent({
            id: crypto.randomUUID(),
            name: "login",
            detail: error instanceof Error ? error.message : "Login polling failed"
          });
        }
      }
    };

    const interval = window.setInterval(() => {
      if (!loginStatus || !TERMINAL_LOGIN_STATES.has(loginStatus.state)) {
        void poll();
      }
    }, 2000);

    void poll();

    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [login?.sessionId, loginStatus?.state, onEvent]);

  async function startLogin() {
    onBusyChange(true);
    try {
      const result = await providerSetupClient.startLogin(QQ_PROVIDER);
      setLogin(result);
      setLoginStatus({
        sessionId: result.sessionId,
        provider: result.provider,
        state: result.state,
        credentialStored: false,
        message: result.message
      });
      onEvent({ id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` });
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <ProviderLoginCard
      providerLabel="QQ Music"
      login={login}
      loginStatus={loginStatus}
      busy={busy}
      onStartLogin={startLogin}
    />
  );
}
