# feed

moved to https://git.gay/pixellight/feed

Support for androidx.paging

Paging is one of these problems that sounds easy on the surface but gets more
and more complex the more you look into it. androidx.paging helps a lot with it
but some interactions are still very difficult to get right. This library both
simplifies those implementations and documents common pitfalls.

Specifically, this library helps with:
1. Persisting and restoring the position in the list.
2. Completely refreshing the contents.
3. Refreshing 'in place', keeping the position on screen.
