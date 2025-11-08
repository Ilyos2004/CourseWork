# Создание информационной системы для назначения репетиторских занятий

**Курсовая работа, этап №2**  
**Автор:** Сангинов Илёсджон

---

## Оглавление

- [Требования задания](#требования-задания)  
- [ER-модель](#ER-модель)  
- [Датологическая модель](#DDL-модель)  
- [Реализация даталогической модели в реляционной СУБД PostgreSQL](#Реализация-даталогической-модели-в-реляционной-СУБД-PostgreSQL).
- [Триггеры](#Триггеры)  
- [Скрипты для создания,удаления БД,заполнения базы тестовыми данными](#Скрипты-для-создания-,-удаления-БД-,-заполнения-базы-тестовыми-даннымие)  
- [PL/pgSQL-функции и процедуры для выполнения критически важных запросов.](#PL/pgSQL-функции-и-процедуры-для-выполнения-критически-важных-запросов)  
- [Индексы](#Индексы)  

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

## ER-модель
<img alt=" " src="https://github.com/Ilyos2004/CourseWork/blob/dev2/part2/ER-%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C.png">

## DDL-модель
<img alt=" " src="https://github.com/Ilyos2004/CourseWork/blob/dev2/part2/DDL%20-%20%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C.png">

## Реализация даталогической модели в реляционной СУБД PostgreSQL


```sql
-- 1. Role
CREATE TABLE roles (
  role_id SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

-- 2. User
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  full_name TEXT NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone TEXT,
  role_id INT NOT NULL REFERENCES roles(role_id)
);

-- 3. Tutor_Profiles
CREATE TABLE tutor_profiles (
  id SERIAL PRIMARY KEY,
  experience_years INT DEFAULT 0 CHECK (experience_years >= 0),
  info TEXT,
  rating_count INT DEFAULT 0 CHECK (rating_count >= 0),
  languages TEXT,
  user_id INT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE
);

-- 4. Student_Profiles
CREATE TABLE student_profiles (
  id SERIAL PRIMARY KEY,
  preferred_language VARCHAR(100),
  goals TEXT,
  age INT,
  user_id INT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE
);

-- 5. Subjects
CREATE TABLE subjects (
  id SERIAL PRIMARY KEY,
  name VARCHAR(150) NOT NULL UNIQUE
);

-- 6. Tutor_Subjects
CREATE TABLE tutor_subjects (
  id SERIAL PRIMARY KEY,
  subject_id INT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  tutor_id INT NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
  info TEXT,
  count_students INT DEFAULT 0 CHECK (count_students >= 0),
  languages VARCHAR(150)
);

-- 7. Format
CREATE TABLE format (
  id SERIAL PRIMARY KEY,
  type TEXT NOT NULL UNIQUE
);

-- 8. Location
CREATE TABLE location (
  id SERIAL PRIMARY KEY,
  format_id INT REFERENCES format(id),
  info TEXT
);

-- 9. Time_Slot
CREATE TABLE time_slot (
  id SERIAL PRIMARY KEY,
  tutor_id INT NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
  subject_id INT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  location_id INT REFERENCES location(id),
  start_dt TEXT NOT NULL,
  end_dt TEXT NOT NULL,
  capacity INT NOT NULL DEFAULT 1 CHECK (capacity > 0),
  status TEXT
);

-- 10. Booking
CREATE TYPE booking_status AS ENUM ('booked', 'cancelled', 'completed');

CREATE TABLE booking (
  id SERIAL PRIMARY KEY,
  slot_id INT NOT NULL REFERENCES time_slot(id) ON DELETE CASCADE,
  student_id INT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
  booked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status booking_status NOT NULL 
);

-- 11. Review
CREATE TABLE review (
  id SERIAL PRIMARY KEY,
  student_id INT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
  tutorsubject_id INT REFERENCES tutor_subjects(id) ON DELETE SET NULL,
  rating INT CHECK (rating BETWEEN 1 AND 5),
  comment TEXT
);
```
## Триггеры

#### 1️⃣ Проверка — нельзя бронировать слот, который уже начался

```sql
CREATE OR REPLACE FUNCTION check_booking_before_start()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  s timestamptz;
BEGIN
  -- приводим TEXT -> timestamptz 
  SELECT start_dt::timestamptz INTO s
  FROM time_slot
  WHERE id = NEW.slot_id
  LIMIT 1;

  IF s IS NULL THEN
    RAISE EXCEPTION 'Slot % not found (booking before start check)', NEW.slot_id;
  END IF;

  IF s <= now() THEN
    RAISE EXCEPTION 'Cannot book slot %: it already started at %', NEW.slot_id, s;
  END IF;

  RETURN NEW;
END;
$$;
```
#### 2️⃣ Проверка вместимости (capacity) при вставке брони
```sql
CREATE OR REPLACE FUNCTION fn_check_capacity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  cap INT;
  cnt INT;
BEGIN
  -- проверяем только случаи, когда новая запись считается "booked"
  IF NOT (NEW.status IS NULL OR lower(NEW.status) = 'booked') THEN
    RETURN NEW;
  END IF;

  -- если это UPDATE и статус уже был 'booked' — нет роста занятости -> пропускаем
  IF TG_OP = 'UPDATE' AND (OLD.status IS NOT NULL AND lower(OLD.status) = 'booked') THEN
    RETURN NEW;
  END IF;

  -- блокируем эту строку time_slot для корректного подсчёта при конкурентных попытках
  SELECT capacity INTO cap FROM time_slot WHERE id = NEW.slot_id FOR UPDATE;
  IF cap IS NULL THEN
    RAISE EXCEPTION 'Slot % not found (capacity check)', NEW.slot_id;
  END IF;

  -- Считаем текущее количество занятых мест
  SELECT count(*) INTO cnt FROM booking
    WHERE slot_id = NEW.slot_id AND (status IS NULL OR lower(status) = 'booked');

  --Сравниваем с лимитом
  IF cnt >= cap THEN
    RAISE EXCEPTION 'Cannot book slot %: capacity % already reached', NEW.slot_id, cap;
  END IF;
  
  RETURN NEW;
END;
$$;
```

#### 3️⃣ Автоматическая отмена бронирования при отмене слота
```sql
-- Функция: авто-отмена бронирований при переводе слота в status = 'cancelled'
CREATE OR REPLACE FUNCTION fn_auto_cancel_bookings_on_slot_cancel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND OLD.status IS DISTINCT FROM NEW.status
     AND NEW.status IS NOT NULL AND lower(NEW.status) = 'cancelled' THEN

    UPDATE booking
    SET status = 'cancelled', updated_at = now()
    WHERE slot_id = NEW.id
      AND (status IS NULL OR lower(status) <> 'cancelled');
  END IF;

  RETURN NEW;
END;
$$;
```
## Скрипты для создания, удаления БД, заполнения базы тестовыми данными

#### 🏗️ Скрипт для создания БД
```sql
CREATE DATABASE rehearsal_classes;
```
#### 🗑️Скрипт для удаления БД
```sql
DROP DATABASE rehearsal_classes;
```
#### Скрипт для заполнения базы тестовыми данными
```sql
-- Добавление ролей
INSERT INTO roles (name) VALUES
  ('student'),
  ('tutor'),
  ('admin');

-- Добавление пользователей
INSERT INTO "user" (full_name, email, password, phone, role_id)
VALUES
  ('Ivan Ivanov', 'ivan@student.example', 'pwd_hash_ivan', '+7-900-000-0001', 1),
  ('Petr Petrov', 'petr@tutor.example', 'pwd_hash_petr', '+7-900-000-0002', 1),
  ('Anna Annanovna', 'anna@tutor.example', 'pwd_hash_anna', '+7-900-000-0003', 2),
  ('Admin User', 'admin@example', 'pwd_hash_admin', '+7-900-000-0000', 3);

-- Профили студентов
INSERT INTO student_profiles (preferred_language, goals, age, user_id)
VALUES
  ('ru', 'Повысить уровень по математике', 20, 2),
  ('ru', 'Репетитор по математике, подготовка к ЕГЭ', 17, 6);

-- Предметы
INSERT INTO subjects (name) VALUES
  ('Mathematics'),
  ('Physics'),
  ('English');

-- Репетиторы и их предметы
INSERT INTO tutor_subjects (subject_id, tutor_id, info, count_students, languages)
VALUES
  (1, 1, 'Алгебра, анализ, подготовка к ЕГЭ', 0, 'ru'),
  (2, 2, 'Physics, подготовка к ЕГЭ', 15, 'ru,en');

-- Форматы занятий
INSERT INTO format (type)
VALUES
  ('online'),
  ('offline');

-- Локации
INSERT INTO location (format_id, info)
VALUES
  ((SELECT id FROM format WHERE type = 'online'), 'Zoom: https://zoom.example/meet123'),
  ((SELECT id FROM format WHERE type = 'offline'), 'Auditorium 12');

-- Тайм-слоты (доступные занятия)
INSERT INTO time_slot (tutor_id, subject_id, location_id, start_dt, end_dt, capacity, status)
VALUES
(
  (SELECT tp.id FROM tutor_profiles tp
   JOIN "user" u ON tp.user_id = u.id
   WHERE u.email = 'petr@tutor.example'),
  (SELECT id FROM subjects WHERE name = 'Mathematics'),
  2, '2025-09-15 18:00', '2025-09-15 20:00', 2, 'published'
),
(
  (SELECT tp.id FROM tutor_profiles tp
   JOIN "user" u ON tp.user_id = u.id
   WHERE u.email = 'anna@tutor.example'),
  (SELECT id FROM subjects WHERE name = 'Physics'),
  1, '2025-09-16 17:00', '2025-09-16 19:00', 10, 'published'
);

-- Бронирование занятий
INSERT INTO booking (slot_id, student_id, status)
VALUES
  (1, 2, 'booked');

-- Отзывы студентов
INSERT INTO review (student_id, tutorsubject_id, booking_id, rating, comment)
VALUES
  (1, 1, 1, 5, 'Очень хороший урок, материал хорошо объяснили');
```
## PL/pgSQL-функции и процедуры для выполнения критически важных запросов.

#### 🔍 Получить доступные слоты (возвращает start_dt/end_dt как TEXT)
```sql
CREATE OR REPLACE FUNCTION fn_get_available_slots(p_from timestamptz, p_to timestamptz)
-- Возвращаем таблицу с колонками:
RETURNS TABLE (
  slot_id INT,
  tutor_id INT,
  subject_id INT,
  start_dt TEXT,
  end_dt TEXT,
  capacity INT,
  booked_count INT,
  free_places INT
)
LANGUAGE sql
AS $$
  SELECT ts.id, ts.tutor_id, ts.subject_id, ts.start_dt, ts.end_dt, ts.capacity,
         COALESCE(bc.cnt, 0) AS booked_count,
         ts.capacity - COALESCE(bc.cnt, 0) AS free_places
  FROM time_slot ts
  LEFT JOIN (
    SELECT slot_id, count(*) AS cnt
    FROM booking
    WHERE (status IS NULL OR lower(status) = 'booked')
    GROUP BY slot_id
  ) bc ON bc.slot_id = ts.id
  WHERE (ts.start_dt::timestamptz) >= p_from
    AND (ts.start_dt::timestamptz) < p_to
    AND ts.capacity - COALESCE(bc.cnt, 0) > 0
  ORDER BY (ts.start_dt::timestamptz);
$$;
```
#### 📝 Добавить отзыв и пересчитать рейтинг репетитора
```sql
CREATE OR REPLACE FUNCTION fn_add_review(p_booking_id INT, p_rating INT, p_comment TEXT)
RETURNS INT
LANGUAGE plpgsql
AS $$
-- Создаем перемены для хранения данных
DECLARE
  v_booking RECORD;              
  v_tutor_profile_id INT;       
  v_new_id INT;                  
  v_cnt INT;                     
  v_avg NUMERIC;                 
BEGIN
  -- Проверяем существование брони
  SELECT * INTO v_booking FROM booking WHERE id = p_booking_id LIMIT 1;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Booking % not found', p_booking_id;
  END IF;

  -- Валидация рейтинга 
  IF p_rating IS NULL OR p_rating < 1 OR p_rating > 5 THEN
    RAISE EXCEPTION 'Rating must be between 1 and 5 (got: %)', p_rating;
  END IF;
  --  Проверяем, чтобы рейтинг был в допустимом диапазоне 1..5

  -- Вставляем новый отзыв 
  INSERT INTO review (student_id, tutorsubject_id, booking_id, rating, comment, created_at)
  VALUES (
    v_booking.student_id,     -- берем id студента из найденной брони
    tutorsubject_id,          -- берем id предмета 
    p_booking_id,             -- связываем отзыв с бронированием
    p_rating,                 -- оценка
    p_comment,                -- комментарий
    now()                     -- время создания
  )
  RETURNING id INTO v_new_id;
  -- сохраняет id только что созданной записи,

  -- Находим id профиля репетитора через слот, связанный с бронированием
  SELECT ts.tutor_id INTO v_tutor_profile_id
  FROM booking b
  JOIN time_slot ts ON b.slot_id = ts.id
  WHERE b.id = p_booking_id
  LIMIT 1;


  -- Пересчёт количества и среднего рейтинга
  IF v_tutor_profile_id IS NOT NULL THEN
    SELECT COUNT(r.id), AVG(r.rating)::numeric(3,2)
      INTO v_cnt, v_avg
    FROM review r
    JOIN booking b2 ON r.booking_id = b2.id
    JOIN time_slot ts2 ON b2.slot_id = ts2.id
    WHERE ts2.tutor_id = v_tutor_profile_id
      AND r.rating IS NOT NULL;

    -- Обновляем профиль репетитора: количество отзывов и средний рейтинг 
    UPDATE tutor_profiles
    SET rating_count = v_cnt,
        rating_avg = ROUND(v_avg:),
        updated_at = now()
    WHERE id = v_tutor_profile_id;
  END IF;

  RETURN v_new_id;
END;
$$;

```

## ⚡ Индексы
```sql
-- 1️⃣ Индекс создан для ускорения выборок ближайших слотов конкретного репетитора по колонкам tutor_id и start_dt.
CREATE INDEX IF NOT EXISTS idx_time_slot_tutor_start ON time_slot(tutor_id, start_dt);
Тип: B-tree
-- Потому что, фильтр по равенству (tutor_id) + диапазон/сортировка по времени (start_dt) —  классический кейс для B-tree, позволяет идти по индексу без лишней сортировки.


-- 2️⃣ Индекс для быстрого поиска и просмотра истории бронирований конкретного студента.
CREATE INDEX IF NOT EXISTS idx_booking_student
  ON booking (student_id);
Тип: B-tree
-- Потому что, частые выборки по равенству (student_id = …); B-tree даёт быстрый Index Scan/Only Scan.

-- 3️⃣ Индекс ускоряет проверки и выборку отзывов, связанных с конкретной бронью. 
CREATE INDEX IF NOT EXISTS idx_review_booking ON review(booking_id);
Тип: B-tree
-- Потому что, точечный поиск по равенству (booking_id) — оптимально для B-tree.

-- 4️⃣ Индекс для ускорения поиска слотов по предмету в заданном интервале дат
и выдачи в порядке времени начала.
CREATE INDEX IF NOT EXISTS idx_time_slot_subject_start
  ON time_slot (subject_id, start_dt);
Тип: B-tree
-- Потому что, селективный фильтр по subject_id + диапазон/ORDER BY по start_dt - B-tree хорошо поддерживает и сравнение по времени, и чтение в нужном порядке.


-- 5️⃣ Индекс для ускорения выборок слотов по статусу (published/cancelled/draft)
--     и дате начала.
CREATE INDEX IF NOT EXISTS idx_time_slot_status_start
  ON time_slot (status, start_dt);
  Тип: B-tree
-- Потому что, фильтр по равенству статуса и выборка по времени; составной B-tree ускоряет любые статусы (published/cancelled/draft) и снижает время сортировки.

```

## Примеры использование индексов 

#### 1) До создания индекса:
```
 EXPLAIN ANALYZE
SELECT id, start_dt
FROM time_slot
WHERE tutor_id = (
  SELECT tp.id FROM tutor_profiles tp
  JOIN users u ON u.id = tp.user_id
  WHERE u.email = 'alice.tutor@example.com'
)
  AND start_dt >= to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF')
ORDER BY start_dt;

 Planning Time: 0.884 ms
 Execution Time: 0.152 ms
```
 #### После создания индекса:
 ```
 EXPLAIN ANALYZE
SELECT id, start_dt
FROM time_slot
WHERE tutor_id = (
        SELECT tp.id
        FROM tutor_profiles tp
        JOIN users u ON u.id = tp.user_id
        WHERE u.email = 'alice.tutor@example.com'
      )
  AND start_dt >= to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF')
ORDER BY start_dt;

 Planning Time: 0.538 ms
 Execution Time: 0.128 ms
```

#### 2) До создания индекса:
```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, slot_id, status, booked_at
FROM booking
WHERE student_id = (
  SELECT sp.id
  FROM student_profiles sp
  JOIN users u ON u.id = sp.user_id
  WHERE u.email = 'dave.student@example.com'  -- подставь нужный e-mail студента
)
ORDER BY booked_at DESC;

 Planning Time: 0.514 ms
 Execution Time: 0.118 ms
```

 #### После создания индекса:
 ```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, slot_id, status, booked_at
FROM booking
WHERE student_id = (
  SELECT sp.id
  FROM student_profiles sp
  JOIN users u ON u.id = sp.user_id
  WHERE u.email = 'dave.student@example.com'
)
ORDER BY booked_at DESC;

 Planning Time: 0.538 ms
 Execution Time: 0.109 ms
```

 #### 3) До создания индекса:
```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, rating, comment
FROM review
WHERE booking_id = (SELECT id FROM booking ORDER BY id LIMIT 1);

 Planning Time: 0.349 ms
 Execution Time: 0.064 ms
```

 #### После создания индекса:
 ```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, rating, comment
FROM review
WHERE booking_id = (SELECT id FROM booking ORDER BY id LIMIT 1);
```
 Planning Time: 0.328 ms

 Execution Time: 0.047 ms

 #### 4) До создания индекса:
```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tutor_id, start_dt
FROM time_slot
WHERE subject_id = (SELECT id FROM subjects WHERE name = 'Math')
  AND start_dt >= '2025-11-01T00:00:00+00'
  AND start_dt <  '2025-12-01T00:00:00+00'
ORDER BY start_dt;

 Planning Time: 0.458 ms
 Execution Time: 0.104 ms
```

 #### После создания индекса:
 ```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tutor_id, start_dt
FROM time_slot
WHERE subject_id = (SELECT id FROM subjects WHERE name = 'Math')
  AND start_dt >= '2025-11-01T00:00:00+00'
  AND start_dt <  '2025-12-01T00:00:00+00'
ORDER BY start_dt;


 Planning Time: 0.382 ms
 Execution Time: 0.092 ms
```

 #### 5) До создания индекса:
```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tutor_id, subject_id, start_dt
FROM time_slot
WHERE status = 'published'
  AND start_dt >= to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF')
ORDER BY start_dt;

 Planning Time: 0.381 ms
 Execution Time: 0.062 ms
```

 #### После создания индекса:
 ```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tutor_id, subject_id, start_dt
FROM time_slot
WHERE status = 'published'
  AND start_dt >= to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF')
ORDER BY start_dt;

 Planning Time: 0.378 ms
 Execution Time: 0.051 ms
```
