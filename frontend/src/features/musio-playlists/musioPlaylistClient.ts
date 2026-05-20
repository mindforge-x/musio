import { api } from "../../shared/api";

export const musioPlaylistClient = {
  list: api.musioPlaylists,
  create: api.createMusioPlaylist,
  removeItem: api.removeMusioPlaylistItem
};
