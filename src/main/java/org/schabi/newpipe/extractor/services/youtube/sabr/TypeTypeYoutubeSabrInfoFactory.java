package org.schabi.newpipe.extractor.services.youtube.sabr;

public final class TypeTypeYoutubeSabrInfoFactory {
    private TypeTypeYoutubeSabrInfoFactory() {
    }

    public static YoutubeSabrInfo withPlaybackUrlAndClientVersion(
            final YoutubeSabrInfo info,
            final String serverAbrStreamingUrl,
            final String clientVersion) {
        return new YoutubeSabrInfo(
                info.getProfile(),
                info.getVideoId(),
                info.getCpn(),
                clientVersion,
                info.getVisitorData(),
                serverAbrStreamingUrl,
                info.getVideoPlaybackUstreamerConfig(),
                info.getFormats(),
                info.getPlayerContextProvider(),
                info.getPlayerPoToken());
    }

    public static YoutubeSabrInfo withPlayerContext(
            final YoutubeSabrInfo info,
            final YoutubeSabrPlayerContextProvider playerContextProvider,
            final String playerPoToken) {
        return new YoutubeSabrInfo(
                info.getProfile(),
                info.getVideoId(),
                info.getCpn(),
                info.getClientVersion(),
                info.getVisitorData(),
                info.getServerAbrStreamingUrl(),
                info.getVideoPlaybackUstreamerConfig(),
                info.getFormats(),
                playerContextProvider,
                playerPoToken);
    }
}
