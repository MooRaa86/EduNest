# EduNest Backend - دليل الفرونت

## المتطلبات
- Docker Desktop مثبت ومشغول

## خطوات التشغيل

### 1. حمّل ملف docker-compose
احفظ الملف ده باسم `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: edunest-mysql
    environment:
      MYSQL_ROOT_PASSWORD: 12345
      MYSQL_DATABASE: edunest
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - edunest-network

  backend:
    image: ghcr.io/mooraa86/edunest-backend:latest
    container_name: edunest-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:mysql://mysql:3306/edunest?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
      DATABASE_NAME: root
      DATABASE_PASSWORD: 12345
      MAIL_HOST: smtp.gmail.com
      MAIL_PORT: 587
      MAIL_USERNAME: mywork3487@gmail.com
      MAIL_PASSWORD: mdkkrkxtfqruvsur
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    networks:
      - edunest-network

volumes:
  mysql-data:

networks:
  edunest-network:
    driver: bridge
```

### 2. شغّل الـ Backend
```bash
docker-compose up -d
```

### 3. تأكد إنه شغال
```bash
docker-compose ps
```

### 4. شوف الـ logs
```bash
docker-compose logs -f backend
```

### 5. جرّب الـ API
- Base URL: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`

## الاتصال بالـ Database (اختياري)

لو عايز تشوف الـ database من MySQL Workbench:
- Host: `localhost`
- Port: `3306`
- Username: `root`
- Password: `12345`
- Database: `edunest`

## تحديث الـ Backend

لما الباك إند يعمل push جديد:
```bash
docker-compose pull backend
docker-compose up -d
```

## إيقاف كل حاجة
```bash
docker-compose down
```

## حذف كل حاجة (بما فيها الـ database)
```bash
docker-compose down -v
```

## مشاكل شائعة

### Port 8080 مستخدم
```bash
# غيّر الـ port في docker-compose.yml
ports:
  - "8081:8080"  # استخدم 8081 بدل 8080
```

### Port 3306 مستخدم (MySQL شغال على الجهاز)
```bash
# غيّر الـ port في docker-compose.yml
ports:
  - "3307:3306"  # استخدم 3307 بدل 3306
```
