plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Ship the real licence inside the jar.
//
// MIT - both the original /dank/null's and this backport's - requires the copyright and permission notices
// to travel with the code, so the jar is exactly where they need to be. src/main/resources/ used to hold a
// template placeholder ("Copyright (c) [year] [fullname]") left over from ExampleMod, and since that is a
// resource directory it was the file every build packaged, notices and all. Pulling the root LICENSE in
// here keeps one source of truth rather than a copy that can silently drift out of date.
tasks.processResources {
    from(rootProject.file("LICENSE"))
}
