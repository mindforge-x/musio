import { api } from "../../shared/api";

export const providerSetupClient = {
  providers: api.providers,
  status: api.providerStatus,
  startLogin: api.startProviderLogin,
  loginStatus: api.providerLoginStatus,
  logout: api.logoutProvider
};
