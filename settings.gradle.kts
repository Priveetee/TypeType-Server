rootProject.name = "typetype-server"

val localPipePipeExtractor = file("../PipePipeExtractor")
if (localPipePipeExtractor.isDirectory) {
    includeBuild(localPipePipeExtractor) {
        dependencySubstitution {
            substitute(module("com.github.InfinityLoop1308.PipePipeExtractor:extractor"))
                .using(project(":extractor"))
        }
    }
}
