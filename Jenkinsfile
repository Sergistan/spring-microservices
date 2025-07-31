pipeline {
    agent any

    tools {
        jdk 'Java21'
    }

    environment {
        DOCKERHUB_NAMESPACE = 'sergistan'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    def services = [
                      'eureka-server','config-server','getaway-server',
                      'order-service','shop-service','payment-service',
                      'notification-service','history-service'
                    ]
                    services.each { svc ->
                        powershell """
                            Write-Host "=== Сборка и тестирование ${svc} ==="
                            cd ${svc}
                            ..\\gradlew.bat clean build --no-daemon
                            cd ..
                        """
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def services = [
                      'eureka-server','config-server','getaway-server',
                      'order-service','shop-service','payment-service',
                      'notification-service','history-service'
                    ]
                    services.each { svc ->
                        powershell """
                            Write-Host "=== Building Docker image for ${svc} ==="
                            docker build -t ${DOCKERHUB_NAMESPACE}/${svc}:${env.BUILD_NUMBER} .\\${svc}
                        """
                    }
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DH_USER',
                    passwordVariable: 'DH_PASS'
                )]) {
                    script {
                        def services = [
                          'eureka-server','config-server','getaway-server',
                          'order-service','shop-service','payment-service',
                          'notification-service','history-service'
                        ]
                        services.each { svc ->
                            powershell """
                                Write-Host '=== Docker Hub login ==='
                                # предварительно выходим, чтобы не было конфликтов
                                docker logout 2>$null; Write-Host 'Logged out'

                                # подаём пароль через stdin
                                \$env:DH_PASS | docker login --username \$env:DH_USER --password-stdin

                                Write-Host "=== Pushing ${svc}:${env.BUILD_NUMBER} ==="
                                docker push ${DOCKERHUB_NAMESPACE}/${svc}:${env.BUILD_NUMBER}
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()    // очищаем workspace
        }
        success {
            echo '✅ Сборка и публикация образов успешно завершены'
        }
        failure {
            echo '❌ Что-то пошло не так, проверьте логи'
        }
    }
}