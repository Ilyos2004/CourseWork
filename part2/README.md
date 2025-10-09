
# Создание информационной системы для назначения репетиторских занятий

**Курсовая работа, этап №2**  
**Автор:** Сангинов Илёсджон

---

## Оглавление

- [Требования задания](#требования-задания)  
- [ER-модель)](images/ER-модель.png)  
- [Датологическая модель](images/DDl-модель.png)  
- [Триггеры (PL/pgSQL)](#триггеры-plpgsql)  
- [PL/pgSQL функции / процедуры](#plpgsql-функции--процедуры)  
- [Seed (тестовые данные)](#seed-тестовые-данные)  
- [Индексы и обоснование](#индексы-и-обоснование)  
- [Инструкции по развёртыванию](#инструкции-по-развёртыванию)  
- [Тестовые сценарии](#тестовые-сценарии)  
- [Рекомендации и замечания](#рекомендации-и-замечания)

---

## Требования задания

1. Построить ER-модель (в PDF/изображении).  
2. Построить даталогическую модель (минимум 10 сущностей, есть M:N).  
3. Реализовать модель в PostgreSQL (DDL).  
4. Обеспечить целостность (DDL, триггеры).  
5. Скрипты создания/удаления БД и seed.  
6. PL/pgSQL-функции/процедуры для критичных операций.  
7. Создать и обосновать индексы.

---

## Схема БД (даталогическая модель)

Основные таблицы: `roles`, `users`, `tutor_profiles`, `student_profiles`, `subjects`, `tutor_subjects`, `format`, `location`, `time_slot`, `booking`, `review`.

> Примечание: вставьте сюда изображение ER/логической диаграммы, например `images/er-diagram.png`:
> ```markdown
> ![ER diagram](images/er-diagram.png)
> ```

---

## SQL: DDL (схема)

> Скопируйте этот блок в `schema.sql` или выполните в psql.

```sql
-- roles
CREATE TABLE roles (
  role_id SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

-- users
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  full_name TEXT NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone TEXT,
  role_id INT NOT NULL REFERENCES roles(role_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- tutor_profiles
CREATE TABLE tutor_profiles (
  id SERIAL PRIMARY KEY,
  experience_years INT DEFAULT 0 CHECK (experience_years >= 0),
  info TEXT,
  rating_count INT DEFAULT 0 CHECK (rating_count >= 0),
  languages TEXT,
  user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- student_profiles
CREATE TABLE student_profiles (
  id SERIAL PRIMARY KEY,
  preferred_language VARCHAR(100),
  goals TEXT,
  age INT,
  user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- subjects
CREATE TABLE subjects (
  id SERIAL PRIMARY KEY,
  name VARCHAR(150) NOT NULL UNIQUE,
  description TEXT
);

-- tutor_subjects
CREATE TABLE tutor_subjects (
  id SERIAL PRIMARY KEY,
  subject_id INT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  tutor_id INT NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
  info TEXT,
  count_students INT DEFAULT 0 CHECK (count_students >= 0),
  languages VARCHAR(150),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- format
CREATE TABLE format (
  id SERIAL PRIMARY KEY,
  type TEXT NOT NULL UNIQUE
);

-- location
CREATE TABLE location (
  id SERIAL PRIMARY KEY,
  format_id INT REFERENCES format(id),
  info TEXT
);

-- time_slot
-- start_dt/end_dt оставлены TEXT (как в вашей схеме). Рекомендую мигрировать в timestamptz позже.
CREATE TABLE time_slot (
  id SERIAL PRIMARY KEY,
  tutor_id INT NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
  subject_id INT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  location_id INT REFERENCES location(id),
  start_dt TEXT NOT NULL,
  end_dt TEXT NOT NULL,
  capacity INT NOT NULL DEFAULT 1 CHECK (capacity > 0),
  status TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- booking
CREATE TABLE booking (
  id SERIAL PRIMARY KEY,
  slot_id INT NOT NULL REFERENCES time_slot(id) ON DELETE CASCADE,
  student_id INT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
  booked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (slot_id, student_id)
);

-- review
CREATE TABLE review (
  id SERIAL PRIMARY KEY,
  student_id INT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
  tutorsubject_id INT REFERENCES tutor_subjects(id) ON DELETE SET NULL,
  booking_id INT REFERENCES booking(id) ON DELETE SET NULL,
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
