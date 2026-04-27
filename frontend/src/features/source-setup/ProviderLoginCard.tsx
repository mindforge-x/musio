import { QrCode } from "lucide-react";
import { LoginStartResult, LoginStatus } from "../../shared/types";

type ProviderLoginCardProps = {
  providerLabel: string;
  login: LoginStartResult | null;
  loginStatus: LoginStatus | null;
  busy: boolean;
  onStartLogin: () => void;
};

export function ProviderLoginCard({ providerLabel, login, loginStatus, busy, onStartLogin }: ProviderLoginCardProps) {
  return (
    <section className="panel auth-panel">
      <div className="panel-heading">
        <h2>{providerLabel}</h2>
        <span>{loginStatus?.state ?? "not linked"}</span>
      </div>
      <div className="qr-box">
        {login?.qrCodeDataUrl ? <img src={login.qrCodeDataUrl} alt={`${providerLabel} login QR`} /> : <QrCode size={96} />}
      </div>
      <button className="secondary-action" type="button" onClick={onStartLogin} disabled={busy}>
        <QrCode size={18} />
        Login
      </button>
    </section>
  );
}
