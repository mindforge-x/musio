import { QrCode, RotateCcw, SkipForward } from "lucide-react";
import { LoginStartResult, LoginStatus } from "../../shared/types";

type ProviderLoginCardProps = {
  providerLabel: string;
  login: LoginStartResult | null;
  loginStatus: LoginStatus | null;
  authenticated: boolean;
  skipped: boolean;
  busy: boolean;
  onStartLogin: () => void;
  onSkip: () => void;
};

export function ProviderLoginCard({
  providerLabel,
  login,
  loginStatus,
  authenticated,
  skipped,
  busy,
  onStartLogin,
  onSkip
}: ProviderLoginCardProps) {
  const state = authenticated || loginStatus?.state === "DONE" ? "已连接" : skipped ? "已跳过" : loginStateLabel(loginStatus?.state);
  const hasQr = Boolean(login?.qrCodeDataUrl);

  return (
    <section className="panel auth-panel">
      <div className="panel-heading">
        <h2>{providerLabel}</h2>
        <span>{state}</span>
      </div>
      <div className="qr-box">
        {hasQr ? <img src={login?.qrCodeDataUrl ?? ""} alt={`${providerLabel} 登录二维码`} /> : <QrCode size={96} />}
      </div>
      <p className="auth-copy">{loginCopy(loginStatus?.state)}</p>
      <div className="auth-actions">
        <button className="primary-action" type="button" onClick={onStartLogin} disabled={busy}>
          {hasQr ? <RotateCcw size={18} /> : <QrCode size={18} />}
          {hasQr ? "刷新二维码" : "开始扫码登录"}
        </button>
        <button className="ghost-action" type="button" onClick={onSkip} disabled={busy || authenticated}>
          <SkipForward size={16} />
          跳过
        </button>
      </div>
    </section>
  );
}

function loginStateLabel(state?: string) {
  switch (state) {
    case "CREATED":
      return "已创建";
    case "NOT_SCANNED":
      return "等待扫码";
    case "SCANNED":
      return "已扫码";
    case "DONE":
      return "已连接";
    case "EXPIRED":
      return "已过期";
    case "FAILED":
      return "失败";
    case "LOGGED_OUT":
      return "已退出";
    default:
      return "未连接";
  }
}

function loginCopy(state?: string) {
  switch (state) {
    case "CREATED":
    case "NOT_SCANNED":
      return "二维码已生成，请使用 QQ 音乐扫码登录。";
    case "SCANNED":
      return "已扫码，请在 QQ 音乐中确认登录。";
    case "DONE":
      return "QQ 音乐已连接，可以进入核心页面。";
    case "EXPIRED":
      return "二维码已过期，请刷新后重新扫码。";
    case "FAILED":
      return "登录失败，可以重试或先进入受限模式。";
    case "LOGGED_OUT":
      return "当前已退出 QQ 音乐登录。";
    default:
      return "使用 QQ 音乐扫码登录；也可以先跳过，进入受限模式。";
  }
}
