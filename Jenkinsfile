pipeline {
    agent any

    environment {
        // Docker Hub credentials stored in Jenkins (username/password)
        DOCKERHUB_CREDENTIALS = credentials('2458a8bf-c1db-46c5-a39f-a6f5061da077')
        DOCKERHUB_NAMESPACE     = 'sergistan'
        GRADLE_OPTS             = '-Dorg.gradle.daemon=false'
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
                    // Список всех модулей с Gradle‑тестами
                    def services = [
                        'eureka-server',
                        'config-server',
                        'getaway-server',
                        'order-service',
                        'shop-service',
                        'payment-service',
                        'notification-service',
                        'history-service'
                    ]
                    services.each { svc ->
                        powershell """
                            Write-Host "=== Тестируем ${svc} ==="
                            cd .\\${svc}
                            ..\\gradlew.bat clean test --no-daemon
                            cd ..\\
                        """
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def services = [
                        'eureka-server',
                        'config-server',
                        'getaway-server',
                        'order-service',
                        'shop-service',
                        'payment-service',
                        'notification-service',
                        'history-service'
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
                    docker.withRegistry('https://registry.hub.docker.com', '2458a8bf-c1db-46c5-a39f-a6f5061da077') {
                        def services = [
                            'eureka-server',
                            'config-server',
                            'getaway-server',
                            'order-service',
                            'shop-service',
                            'payment-service',
                            'notification-service',
                            'history-service'
                        ]
                        services.each { svc ->
                            powershell """
                                Write-Host "=== Pushing ${svc}:${env.BUILD_NUMBER} to Docker Hub ==="
                                docker push ${DOCKERHUB_NAMESPACE}/${svc}:${env.BUILD_NUMBER}
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Сборка и публикация образов успешно завершены'
        }
        failure {
            echo '❌ Что‑то пошло не так, проверьте логи'
        }
    }
}