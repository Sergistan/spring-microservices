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
                        'eureka-server', 'config-server', 'getaway-server',
                        'order-service', 'shop-service', 'payment-service',
                        'notification-service', 'history-service'
                    ]
                    services.each { svc ->
                        powershell """
                            Write-Host "=== Собираем и тестируем ${svc} ==="
                            Push-Location .\\${svc}
                            ..\\gradlew.bat clean build --no-daemon
                            Pop-Location
                        """
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def services = [
                        'eureka-server', 'config-server', 'getaway-server',
                        'order-service', 'shop-service', 'payment-service',
                        'notification-service', 'history-service'
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
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DH_USER',
                        passwordVariable: 'DH_PASS'
                    )]) {
                        powershell """
                            Write-Host "=== Logging in to Docker Hub ==="
                            docker logout || Write-Host 'No previous login'
                            docker login -u $env:DH_USER --password-stdin <<< $env:DH_PASS
                        """
                        def services = [
                            'eureka-server', 'config-server', 'getaway-server',
                            'order-service', 'shop-service', 'payment-service',
                            'notification-service', 'history-service'
                        ]
                        services.each { svc ->
                            powershell """
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
            cleanWs()
        }
        success {
            echo '✅ Сборка и публикация образов успешно завершены'
        }
        failure {
            echo '❌ Что-то пошло не так, проверьте логи'
        }
    }
}