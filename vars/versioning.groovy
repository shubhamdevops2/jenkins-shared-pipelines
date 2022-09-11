void call(Boolean ecrEnabled, String ecrRepoName, String targetFile, String branch, Boolean includeBranchName = true){
    def registryPath = "shubhamdevops2/"
    def newVersion
    def node = readJSON file: targetFile
    def originalversion = node.version
    def baseVersion = node.version.replaceAll(/-[a-zA-Z0-9\/_]+-*[a-zA-Z0-9_]*-*[a-zA-Z0-9_]*-*[a-zA-Z0-9_]*/, '')
    
    def releaseVersion = "${baseVersion}"
    def majorVersion = baseVersion.split("\\.", -1)[0]
    def minorVersion = baseVersion.split("\\.", -1)[1]
    def buildVersion = baseVersion.split("\\.", -1)[2]

    echo "build version is: $buildVersion"
    buildVersion = buildVersion.toInteger() + 1
    def newPomVersion = "${majorVersion}.${minorVersion}.${buildVersion}"

    def artifactId = ecrRepoName.toLowerCase()
    def timestamp = new Date().format("yyyy-MM-dd-HH-mm-ss", TimeZone.getTimeZone('IST'))
    def imageName = (registryPath + artifactId).toLowerCase()
    def imageVars = baseVersion + '-' + timestamp + '-' + branch
    def imageTag = "${imageName}:${imageVars}"   

    echo """###########################################
            |############# Version Details #############
            |##########################################
            |Compenent is : ${artifactId}
            |Current Artiactory version : ${originalversion}
            |Release version to be : ${releaseVersion}
            |New Artiactory version : ${newPomVersion}
            |Docker Image will be : ${imageTag}
            |##########################################""".stripMargin()

    return [originalversion, releaseVersion, newPomVersion, imageTag]
}