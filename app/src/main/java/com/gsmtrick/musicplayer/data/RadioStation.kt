package com.gsmtrick.musicplayer.data

/**
 * A user-saved internet radio station. The [streamUrl] should point to a
 * direct audio stream (mp3 / aac / icecast). ExoPlayer plays it via the
 * standard MediaItem flow, so HLS (.m3u8) is also supported out of the box.
 */
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val country: String = "",
    val tags: String = "",
)

/** Curated default stations, with extra weight on Bangla / South Asian. */
object DefaultRadioStations {
    val all: List<RadioStation> = listOf(
        RadioStation(
            id = "radio-foorti",
            name = "Radio Foorti 88.0 (BD)",
            streamUrl = "https://stream.zeno.fm/4eu5dwcvvxquv",
            country = "Bangladesh",
            tags = "Bangla, Pop",
        ),
        RadioStation(
            id = "abc-radio",
            name = "ABC Radio 89.2 (BD)",
            streamUrl = "https://stream.zeno.fm/9c43d57b-e8b7-4e36-9bf2-5dd2389e5d00",
            country = "Bangladesh",
            tags = "Bangla, Talk",
        ),
        RadioStation(
            id = "radio-bhumi",
            name = "Radio Bhumi 92.8 (BD)",
            streamUrl = "https://stream.zeno.fm/f6yzftgrnuhvv",
            country = "Bangladesh",
            tags = "Bangla, Folk",
        ),
        RadioStation(
            id = "dhaka-fm",
            name = "Dhaka FM 90.4 (BD)",
            streamUrl = "https://stream.zeno.fm/dpjf7qjndchvv",
            country = "Bangladesh",
            tags = "Bangla, Pop",
        ),
        RadioStation(
            id = "bbc-bangla",
            name = "BBC Bangla",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_bangla_radio",
            country = "UK",
            tags = "Bangla, News",
        ),
        RadioStation(
            id = "akashvani-kolkata",
            name = "Akashvani Kolkata-A",
            streamUrl = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio060/playlist.m3u8",
            country = "India",
            tags = "Bangla, AIR",
        ),
        RadioStation(
            id = "soma-fm-groove",
            name = "SomaFM — Groove Salad",
            streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
            country = "USA",
            tags = "Ambient, Chillout",
        ),
    )
}
