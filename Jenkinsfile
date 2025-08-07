pipeline {
  agent any
  tools { jdk 'Java21' }
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
              Write-Host "=== Собираем и тестируем ${svc} ==="
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
        script {
          docker.withRegistry('', 'dockerhub-creds') {
            def services = [
              'eureka-server','config-server','getaway-server',
              'order-service','shop-service','payment-service',
              'notification-service','history-service'
            ]
            services.each { svc ->
              powershell "docker push ${DOCKERHUB_NAMESPACE}/${svc}:${env.BUILD_NUMBER}"
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