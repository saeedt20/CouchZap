# Official playlist test snapshots

Retrieved 2026-10-14 from the official public project endpoints. These are parser/catalog test fixtures, not bundled app playlists. The app downloads the current selected source. No channel is imported automatically.

| Fixture | Official source | SHA-256 of original response |
| --- | --- | --- |
| free-all.txt.gz | https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8 | e2acc617a02149c79a3454bdc2cd69a909eb1fbffe01d19faf1ec096c29f916c |
| free-iran.txt.gz | https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_iran.m3u8 | 4cf11d0b6eb16723af26539a90f2c38be01eb728c94d6c41fc489fcc7df26bcd |
| iptv-iran.txt.gz | https://iptv-org.github.io/iptv/countries/ir.m3u | b6606c699139a54b2732c769a0adb3753ce9a2ba02961705503d3045a7e8b541 |
| free-catalog.json | https://api.github.com/repos/Free-TV/IPTV/contents/playlists?ref=master | 53c3f042caaa42ab782f3bd2cf6bd1228702dae21494f7c4ffc6ddaa11041850 |
| iptv-countries.json | https://iptv-org.github.io/api/countries.json | 0c0e40f0647e9392ca7bf831af9f1f37b2761f3359b02e5951f384a9a95a7da0 |

The compressed files contain the original responses. Country and themed-file choices are derived from validated Free-TV filenames and paths. Provider names and metadata are retained for attribution. Live stream availability and regional restrictions can change.
