package org.common

//Get the quality gate id associated with the project
//If the quality artifactId is not found in the file then it will use the default settings
def getProjectGate(artifactId){
    println "Getting the quality gate for ${artifactId} from qualityGateDefaults.json"
    def finalGate = getQualityGateFromSource(artifactId)

    if(finalGate == null){
        println "No quality gate for ${artifactId} qualityGateDefaults.json...Getting deafault"
        finalGate = getQualityGateFromSource("default")
    }
    return finalGate
}

//Return the given qualityID
def getQualityGateFromSource(artifactId){
    def finalGate
    def qualityGateDefaultRaw = libraryResource 'in/temp/sonar/qualityGateDefaults.json'
    def qualityGateDefaultList = readJSON text: qualityGateDefaultRaw   

    qualityGateDefaultList.qualityGates.each { itx ->
        itx.components.enforced.each { itEnforced ->
            if ( itEnforced == artifactId ){
                println "Found a quality gate for ${artifactId}. Gate is = " + itx.qualityGateName
                finalGate = itx.qualityGateID
            }
        }
        itx.components.advisory.each { itAdvisory ->
            if ( itAdvisory == artifactId ){
                println "Found a quality gate for ${artifactId}. Gate is = " + itx.qualityGateName
                finalGate = itx.qualityGateID
            }
        }
    }
    return finalGate
}