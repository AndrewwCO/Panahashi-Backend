# Panahashi Backend 🍞

Backend REST para la app Panahashi, construido con **Ktor (Kotlin)** y **Firebase** (Auth + Firestore).

## Stack

| Tecnología | Uso |
|------------|-----|
| Kotlin 1.9 | Lenguaje principal |
| Ktor 2.3 | Framework HTTP |
| Firebase Admin SDK | Auth + Firestore |
| Kotlinx Serialization | JSON |
| Gradle | Build tool |

---

## Estructura del proyecto

```
src/main/kotlin/com/panahashi/
├── Application.kt           # Entry point
├── config/
│   ├── FirebaseConfig.kt    # Inicialización Firebase
│   ├── Plugins.kt           # Auth, CORS, JSON, errores
│   └── Routing.kt           # Registro de rutas
├── models/
│   └── Models.kt            # Data classes (Product, Order, User...)
├── routes/
│   ├── ProductRoutes.kt
│   ├── OrderRoutes.kt
│   ├── UserRoutes.kt
│   └── HealthRoutes.kt
└── services/
    ├── FirestoreService.kt  # Abstracción de Firestore
    ├── ProductService.kt
    ├── OrderService.kt
    └── UserService.kt
```

---

## Setup

### 1. Requisitos
- JDK 17+
- Gradle 8+
- Proyecto Firebase con Firestore y Authentication habilitados

### 2. Firebase - Clave de servicio

1. Ve a **Firebase Console → Configuración del proyecto → Cuentas de servicio**
2. Haz click en **"Generar nueva clave privada"**
3. Guarda el archivo como `serviceAccountKey.json` en la raíz del proyecto
4. **Nunca subas este archivo a git** (está en `.gitignore`)

### 3. Configurar Firestore

En Firebase Console, crea una base de datos Firestore con estas colecciones:
- `products` — catálogo de productos
- `orders` — órdenes de clientes
- `users` — perfiles de usuario

### 4. Ejecutar

```bash
./gradlew run
```

El servidor inicia en `http://localhost:8080`

---

## API Endpoints

### Health
```
GET /health
```

### Productos (público)
```
GET    /api/v1/products          # Listar productos disponibles
GET    /api/v1/products/{id}     # Obtener producto por ID
```

### Productos (requiere token Firebase)
```
POST   /api/v1/products          # Crear producto
PATCH  /api/v1/products/{id}/stock  # Actualizar stock
DELETE /api/v1/products/{id}     # Eliminar producto
POST   /api/v1/products/seed     # Cargar productos iniciales
```

### Órdenes (requiere token Firebase)
```
POST   /api/v1/orders            # Crear orden
GET    /api/v1/orders/me         # Mis órdenes
GET    /api/v1/orders/{id}       # Orden por ID
PATCH  /api/v1/orders/{id}/status  # Cambiar estado
GET    /api/v1/orders/all        # Todas las órdenes (admin)
```

### Usuarios (requiere token Firebase)
```
GET    /api/v1/users/me          # Mi perfil
PATCH  /api/v1/users/me          # Actualizar perfil
```

---

## Autenticación

Todas las rutas protegidas requieren un header:

```
Authorization: Bearer <firebase-id-token>
```

El token se obtiene en el cliente React Native con:
```javascript
const token = await auth().currentUser.getIdToken();
```

---

## Crear una orden (ejemplo)

```json
POST /api/v1/orders
Authorization: Bearer <token>

{
  "items": [
    {
      "productId": "abc123",
      "qty": 2,
      "pickupTime": "10:30"
    }
  ],
  "pickupTime": "10:30"
}
```

Respuesta:
```json
{
  "success": true,
  "data": {
    "id": "xyz789",
    "status": "PENDING",
    "total": 9.0,
    "qrCode": "A1B2C3D4-...",
    ...
  }
}
```

---

## Variables de entorno (producción)

```env
PORT=8080
FIREBASE_DATABASE_URL=https://tu-proyecto-default-rtdb.firebaseio.com
GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
```
