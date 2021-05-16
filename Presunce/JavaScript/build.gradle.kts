plugins {
    base
}

val zipJavascriptFiles by tasks.registering(Zip::class) {
    from("src/main/js")
    destinationDirectory.set(temporaryDir)
}

artifacts {
    add("archives", zipJavascriptFiles)
}
