package com.musio.agent.recommendation;

import com.musio.model.Comment;
import com.musio.model.LoginStartResult;
import com.musio.model.LoginStatus;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import com.musio.providers.MusicProvider;
import com.musio.providers.MusicProviderGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongResolverTest {
    @Test
    void resolvesStrictTitleAndArtistMatchEvenWhenFirstResultIsWrong() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("安静", "周杰伦", "适合深夜写代码。")
        ), 1);

        assertEquals(1, result.resolved().size());
        assertEquals("安静", result.resolved().getFirst().song().title());
        assertEquals("qqmusic:quiet", result.resolved().getFirst().song().id());
        assertTrue(result.unresolved().isEmpty());
    }

    @Test
    void keepsCandidateUnresolvedWhenArtistDoesNotMatch() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("安静", "林俊杰", "故意制造歌手不匹配。")
        ), 1);

        assertTrue(result.resolved().isEmpty());
        assertEquals(1, result.unresolved().size());
        assertEquals("安静", result.unresolved().getFirst().title());
    }

    @Test
    void resolvesSafeLiveVersionWhenExactTitleIsUnavailable() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("夜曲", "周杰伦", "安全版本后缀可以接受。")
        ), 1);

        assertEquals(1, result.resolved().size());
        assertEquals("夜曲 (Live)", result.resolved().getFirst().song().title());
    }

    @Test
    void resolvesCombinedArtistNameWhenExpectedArtistIsPresent() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("普通朋友", "陶喆", "联合歌手字段里包含目标歌手。")
        ), 1);

        assertEquals(1, result.resolved().size());
        assertEquals("普通朋友", result.resolved().getFirst().song().title());
    }

    @Test
    void keepsUnsafeDjVersionUnresolved() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("深夜的酒", "大城", "DJ 版本不应当冒充原曲。")
        ), 1);

        assertTrue(result.resolved().isEmpty());
        assertEquals(1, result.unresolved().size());
    }

    @Test
    void resolvesTranslatedTitleAliasInParentheses() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("给你宇宙", "脸红的思春期", "括号里的中文译名应当可匹配。")
        ), 1);

        assertEquals(1, result.resolved().size());
        assertEquals("우주를 줄게 (给你宇宙)", result.resolved().getFirst().song().title());
    }

    @Test
    void resolvesOriginalTitleWhenProviderTitleContainsTranslatedAlias() {
        SongResolver resolver = new SongResolver(new MusicProviderGateway(List.of(new FakeProvider())));

        RecommendationResult result = resolver.resolve(List.of(
                new RecommendationCandidate("우주를 줄게", "脸红的思春期", "原文标题也应当可匹配带译名的结果。")
        ), 1);

        assertEquals(1, result.resolved().size());
        assertEquals("우주를 줄게 (给你宇宙)", result.resolved().getFirst().song().title());
    }

    private static final class FakeProvider implements MusicProvider {
        @Override
        public ProviderType type() {
            return ProviderType.QQMUSIC;
        }

        @Override
        public List<Song> searchSongs(String keyword, int limit) {
            if ("安静 周杰伦".equals(keyword)) {
                return List.of(
                        new Song("qqmusic:rain", ProviderType.QQMUSIC, "自然下雨声", List.of("苏言"), "深夜的雨", 180, null),
                        new Song("qqmusic:quiet", ProviderType.QQMUSIC, "安静", List.of("周杰伦"), "范特西", 334, null)
                ).stream().limit(limit).toList();
            }
            if ("安静 林俊杰".equals(keyword)) {
                return List.of(new Song("qqmusic:quiet", ProviderType.QQMUSIC, "安静", List.of("周杰伦"), "范特西", 334, null));
            }
            if ("夜曲 周杰伦".equals(keyword)) {
                return List.of(new Song("qqmusic:nocturne-live", ProviderType.QQMUSIC, "夜曲 (Live)", List.of("周杰伦"), "现场专辑", 260, null));
            }
            if ("普通朋友 陶喆".equals(keyword)) {
                return List.of(new Song("qqmusic:regular-friend", ProviderType.QQMUSIC, "普通朋友", List.of("陶喆 / 蔡健雅"), "I'm OK", 260, null));
            }
            if ("深夜的酒 大城".equals(keyword)) {
                return List.of(new Song("qqmusic:late-wine-dj", ProviderType.QQMUSIC, "深夜的酒 (DJ晨晨版)", List.of("大城"), "深夜的酒", 190, null));
            }
            if ("给你宇宙 脸红的思春期".equals(keyword) || "우주를 줄게 脸红的思春期".equals(keyword)) {
                return List.of(new Song("qqmusic:universe", ProviderType.QQMUSIC, "우주를 줄게 (给你宇宙)", List.of("脸红的思春期"), "RED PLANET", 214, null));
            }
            return List.of();
        }

        @Override
        public LoginStartResult startLogin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginStatus checkLogin(String loginId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile getProfile(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Playlist> getPlaylists(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Song> getPlaylistSongs(String playlistId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SongDetail getSongDetail(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SongUrl getSongUrl(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Lyrics getLyrics(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Comment> getComments(String songId) {
            throw new UnsupportedOperationException();
        }
    }
}
