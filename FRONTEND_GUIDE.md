# EduNest Backend - للفريق الأمامي

## سحب آخر تحديث

```bash
docker pull ghcr.io/YOUR_GITHUB_USERNAME/edunest-backend:latest
docker stop edunest-backend 2>/dev/null || true
docker rm edunest-backend 2>/dev/null || true
docker run -d -p 8080:8080 --name edunest-backend \
  -e DATABASE_URL=jdbc:mysql://YOUR_DB_HOST:3306/edunest \
  -e DATABASE_NAME=root \
  -e DATABASE_PASSWORD=YOUR_PASSWORD \
  -e MAIL_HOST=smtp.gmail.com \
  -e MAIL_PORT=587 \
  -e MAIL_USERNAME=mywork3487@gmail.com \
  -e MAIL_PASSWORD=mdkkrkxtfqruvsur \
  ghcr.io/YOUR_GITHUB_USERNAME/edunest-backend:latest
```

## API Endpoints
- Base URL: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`

## ملاحظة
كل ما الباك إند يعمل push، الـ image بتتحدث تلقائياً.
اعمل `docker pull` عشان تجيب آخر نسخة.
