// =============================================================================
//  Pipeline Jenkins — events-microservices (phase 11)
//  Job « events-microservices » : dépôt LOCAL servi en lecture seule par git daemon (ci/README.md).
//  Étapes : changements → build et tests (JaCoCo bloquant) → en parallèle Sonar et frontend →
//  quality gate → images Docker des services modifiés + Trivy (en parallèle) → push et
//  déploiement AWS, désactivés par défaut.
// =============================================================================
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 60, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'TOUT_RECONSTRUIRE', defaultValue: false,
                description: 'Ignorer la détection des changements : toutes les images sont reconstruites et scannées')
        booleanParam(name: 'PUSH_IMAGES', defaultValue: false,
                description: 'Pousser les images sur Docker Hub (identifiant Jenkins « dockerHub »). Désactivé tant que le dépôt n\'est pas publié')
        booleanParam(name: 'DEPLOY_AWS', defaultValue: false,
                description: 'Déployer sur l\'EC2 (infra/aws, AWS-RUNBOOK.md). Désactivé : AWS Academy n\'est pas rechargé')
        booleanParam(name: 'TRIVY_BLOQUANT', defaultValue: false,
                description: 'Faire échouer le build si Trivy trouve une vulnérabilité CRITICAL corrigeable')
    }

    // Webhook GitHub : PRÉPARÉ, NON ACTIVÉ (aucun dépôt distant). Après le push, décommenter
    // githubPush() et ajouter le webhook côté GitHub : voir docs/APRES-PUSH.md, section Jenkins.
    // En local, la scrutation (pollSCM, configurée dans le job) détecte les nouveaux commits.
    // triggers { githubPush() }

    environment {
        SONAR_URL  = 'http://sonarqube:9000'
        TAG        = "ci-${env.BUILD_NUMBER}"
        TRIVY      = 'aquasec/trivy:0.65.0'
        NODE_IMAGE = 'node:22-alpine'
    }

    stages {
        stage('Changements') {
            steps {
                script {
                    def reference = params.TOUT_RECONSTRUIRE ? '' : (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: '')
                    sh "bash ci/changements.sh '${reference}' ci-changements.properties"
                    // lecture sans le plugin Pipeline Utility Steps (absent de ce Jenkins)
                    def lire = { cle -> sh(returnStdout: true, script: "sed -n 's/^${cle}=//p' ci-changements.properties").trim() }
                    env.SERVICES = lire('SERVICES')
                    env.FRONTEND = lire('FRONTEND')
                    env.JAVA = lire('JAVA')
                    currentBuild.description = "services : ${env.SERVICES ?: 'aucun'}"
                }
            }
        }

        stage('Build et tests') {
            when { expression { env.JAVA == 'true' } }
            steps {
                // Tout le réacteur : les tests et le seuil JaCoCo (phase 10) protègent aussi les
                // services qui dépendent d'un module modifié ; c'est aussi ce que Sonar analyse.
                sh 'mvn -B -ntp clean verify'   // clean : espace de travail persistant, sinon des JAR « à jour » gardent d'anciennes dépendances
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: '**/target/site/jacoco/jacoco.csv', allowEmptyArchive: true
                }
            }
        }

        stage('Analyses en parallèle') {
            parallel {
                stage('SonarQube') {
                    when { expression { env.JAVA == 'true' } }
                    steps {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh '''mvn -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar \
                                  -Dsonar.projectKey=events-microservices \
                                  -Dsonar.projectName=events-microservices \
                                  -Dsonar.host.url=$SONAR_URL \
                                  -Dsonar.login=$SONAR_TOKEN'''
                        }
                    }
                }
                stage('Frontend') {
                    when { expression { env.FRONTEND == 'true' } }
                    steps {
                        // node:22 lancé par le démon Docker de l'hôte ; --volumes-from donne accès
                        // à l'espace de travail de Jenkins au même chemin.
                        sh '''docker run --rm --volumes-from jenkins -w "$WORKSPACE/frontend" $NODE_IMAGE \
                                sh -c "npm ci --no-audit --no-fund && npm test && npm run build"'''
                    }
                }
            }
        }

        stage('Quality gate') {
            when { expression { env.JAVA == 'true' } }
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh 'bash ci/quality-gate.sh target/sonar/report-task.txt'
                }
            }
        }

        stage('Images Docker et Trivy') {
            when { expression { env.SERVICES?.trim() } }
            steps {
                script {
                    sh 'mkdir -p rapports'
                    // base de vulnérabilités téléchargée une fois, avant les scans en parallèle
                    sh 'docker run --rm -v events-trivy-cache:/root/.cache $TRIVY image --quiet --download-db-only'
                    def branches = [:]
                    env.SERVICES.split(',').findAll { it }.each { service ->
                        branches[service] = {
                            sh "docker build -q -t events/${service}:${TAG} ${service}"
                            sh """docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v events-trivy-cache:/root/.cache \
                                    --volumes-from jenkins ${TRIVY} image --quiet --scanners vuln --severity HIGH,CRITICAL \
                                    --ignore-unfixed --format table -o "${WORKSPACE}/rapports/trivy-${service}.txt" events/${service}:${TAG}"""
                            sh "echo \"${service} : \$(grep -m1 -E '^Total:' rapports/trivy-${service}.txt || echo 'Total: 0 (aucune vulnérabilité HIGH/CRITICAL corrigeable)')\""
                            if (params.TRIVY_BLOQUANT) {
                                sh """docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v events-trivy-cache:/root/.cache \
                                        ${TRIVY} image --quiet --scanners vuln --severity CRITICAL --ignore-unfixed --exit-code 1 \
                                        events/${service}:${TAG}"""
                            }
                        }
                    }
                    parallel branches
                }
            }
            post {
                always { archiveArtifacts artifacts: 'rapports/trivy-*.txt', allowEmptyArchive: true }
            }
        }

        stage('Push des images') {
            when { expression { params.PUSH_IMAGES && env.SERVICES?.trim() } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerHub', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                    sh '''echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                          for s in $(echo "$SERVICES" | tr ',' ' '); do
                            docker tag events/$s:$TAG $DH_USER/events-$s:$TAG
                            docker push $DH_USER/events-$s:$TAG
                          done
                          docker logout'''
                }
            }
        }

        stage('Déploiement AWS') {
            when { expression { params.DEPLOY_AWS } }
            steps {
                withCredentials([string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                                 string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                                 string(credentialsId: 'aws-session-token', variable: 'AWS_SESSION_TOKEN')]) {
                    // DEPLOY_AWS=true vaut confirmation : pas de question possible en CI
                    sh 'CONFIRMER=oui bash infra/aws/deployer.sh'
                }
            }
        }
    }

    post {
        always {
            echo "Build ${currentBuild.currentResult} — services : ${env.SERVICES ?: 'aucun'} ; PUSH_IMAGES=${params.PUSH_IMAGES} ; DEPLOY_AWS=${params.DEPLOY_AWS}"
        }
    }
}
