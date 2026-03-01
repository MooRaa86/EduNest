# EduNest Backend - Docker Setup

## للفريق الأمامي (Frontend Team)

### استخدام Docker Hub

```bash
# 1. سحب الـ image
docker pull YOUR_DOCKERHUB_USERNAME/edunest-backend:latest

# 2. تشغيل الـ container
docker run -d \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:mysql://YOUR_DB_HOST:3306/edunest \
  -e DATABASE_NAME=root \
  -e DATABASE_PASSWORD=12345 \
  -e MAIL_HOST=smtp.gmail.com \
  -e MAIL_PORT=587 \
  -e MAIL_USERNAME=mywork3487@gmail.com \
  -e MAIL_PASSWORD=mdkkrkxtfqruvsur \
  --name edunest-backend \
  YOUR_DOCKERHUB_USERNAME/edunest-backend:latest
```

### API متاح على
- Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## للباك إند (Backend Team)

### رفع الـ image على Docker Hub

```bash
# 1. بناء الـ image
docker build -t edunest-backend:latest .

# 2. تسجيل الدخول لـ Docker Hub
docker login

# 3. عمل tag للـ image
docker tag edunest-backend:latest YOUR_DOCKERHUB_USERNAME/edunest-backend:latest

# 4. رفع الـ image
docker push YOUR_DOCKERHUB_USERNAME/edunest-backend:latest
```


---

## الخيار 2: GitHub Container Registry (تلقائي)

### للباك إند:
1. ارفع الكود على GitHub
2. الـ workflow هيبني الـ image تلقائياً
3. شارك الرابط مع الفرونت

### للفرونت:
```bash
# سحب الـ image
docker pull ghcr.io/YOUR_GITHUB_USERNAME/edunest-backend:latest

# تشغيل
docker run -d -p 8080:8080 \
  -e DATABASE_URL=jdbc:mysql://YOUR_DB_HOST:3306/edunest \
  -e DATABASE_NAME=root \
  -e DATABASE_PASSWORD=12345 \
  -e MAIL_HOST=smtp.gmail.com \
  -e MAIL_PORT=587 \
  -e MAIL_USERNAME=mywork3487@gmail.com \
  -e MAIL_PASSWORD=mdkkrkxtfqruvsur \
  ghcr.io/YOUR_GITHUB_USERNAME/edunest-backend:latest
```

---

## الخيار 3: حفظ الـ image كملف

### للباك إند:
```bash
# بناء الـ image
docker build -t edunest-backend:latest .

# حفظ كملف
docker save edunest-backend:latest -o edunest-backend.tar
```

### للفرونت:
```bash
# تحميل الـ image من الملف
docker load -i edunest-backend.tar

# تشغيل
docker run -d -p 8080:8080 \
  -e DATABASE_URL=jdbc:mysql://YOUR_DB_HOST:3306/edunest \
  -e DATABASE_NAME=root \
  -e DATABASE_PASSWORD=12345 \
  ghcr.io/YOUR_GITHUB_USERNAME/edunest-backend:latest
```
