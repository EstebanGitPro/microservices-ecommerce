services:
  # 1. El Servicio de Base de Datos
  mongodb:
    image: mongo:7.0.4
    container_name: mongodb
    ports:
      - "27017:27017"
    environment:
      - MONGO_INITDB_ROOT_USERNAME=root
      - MONGO_INITDB_ROOT_PASSWORD=password
    volumes:
      - mongo_data:/data/db  # <--- ¡Aquí salvamos los datos!

  # 2. Nuestra Aplicación (Product Service)
  product-service:
    build: .              # Usa el Dockerfile de la carpeta actual
    container_name: product-service
    ports:
      - "8080:8080"
    environment:
      # Sobrescribimos la config de Spring para usar Docker
      # Fíjate: usamos 'mongodb' en lugar de 'localhost'
      - SPRING_DATA_MONGODB_URI=mongodb://root:password@mongodb:27017/product-db?authSource=admin
    depends_on:
      - mongodb           # Espera a que la BD arranque primero

volumes:
  mongo_data:             # Declaramos el volumen persistente