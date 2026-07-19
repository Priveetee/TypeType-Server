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
                info.getFormats());
    }
}
