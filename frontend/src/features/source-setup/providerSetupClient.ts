import { api } from "../../shared/api";

export const providerSetupClient = {
  providers: api.providers,
  status: api.providerStatus,
  musicGene: api.providerMusicGene,
  startLogin: api.startProviderLogin,
  loginStatus: api.providerLoginStatus,
  logout: api.logoutProvider
};
