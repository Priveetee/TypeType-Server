package org.schabi.newpipe.extractor.services.youtube.sabr;

public final class TypeTypeYoutubeSabrInfoFactory {
    private TypeTypeYoutubeSabrInfoFactory() {
    }

    public static YoutubeSabrInfo withPlaybackIdentity(
            final YoutubeSabrInfo info,
            final String serverAbrStreamingUrl,
            final String clientVersion,
            final String cpn,
            final String visitorData,
            final String playerPoToken) {
        return new YoutubeSabrInfo(
                info.getProfile(),
                info.getVideoId(),
                cpn,
                clientVersion,
                visitorData,
                serverAbrStreamingUrl,
                info.getVideoPlaybackUstreamerConfig(),
                info.getFormats(),
                info.getReloadPlaybackParamsToken(),
                playerPoToken);
    }

    public static YoutubeSabrInfo withPlayerIdentity(
            final YoutubeSabrInfo info,
            final String visitorData,
            final String playerPoToken) {
        return new YoutubeSabrInfo(
                info.getProfile(),
                info.getVideoId(),
                info.getCpn(),
                info.getClientVersion(),
                visitorData,
                info.getServerAbrStreamingUrl(),
                info.getVideoPlaybackUstreamerConfig(),
                info.getFormats(),
                info.getReloadPlaybackParamsToken(),
                playerPoToken);
    }
}
