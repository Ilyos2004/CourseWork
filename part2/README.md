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
  full_name TEXT NOT NULL UNIQUE
);

-- 2. User
CREATE TABLE IF NOT EXISTS users (
  id        SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  email     VARCHAR(100) NOT NULL UNIQUE,
  password  VARCHAR(255) NOT NULL,
  phone     TEXT,
  role_id   INT NOT NULL REFERENCES roles(role_id)
);

-- 3. Tutor_Profiles
CREATE TABLE tutor_profiles (
  id               SERIAL PRIMARY KEY,
  experience_years INT  DEFAULT 0 CHECK (experience_years >= 0),
  info             TEXT,
  rating_count     INT  DEFAULT 0 CHECK (rating_count >= 0),
  languages        TEXT,
  user_id          INT  NOT NULL REFERENCES users(id)
);

-- 4. Student_Profiles
CREATE TABLE student_profiles (
  id                 SERIAL PRIMARY KEY,
  preferred_language VARCHAR(100),
  goals              TEXT,
  age                INT,
  user_id            INT NOT NULL REFERENCES users(id)
);


-- 5. Subjects
CREATE TABLE subjects (
  id   SERIAL PRIMARY KEY,
  name VARCHAR(150) NOT NULL UNIQUE
);

-- 6. Tutor_Subjects
CREATE TABLE tutor_subjects (
  id             SERIAL PRIMARY KEY,
  subject_id     INT NOT NULL REFERENCES subjects(id),
  tutor_id       INT NOT NULL REFERENCES tutor_profiles(id),
  info           TEXT,
  count_students INT DEFAULT 0 CHECK (count_students >= 0),
  languages      VARCHAR(150)
);


-- 7. Format
CREATE TABLE format (
  id   SERIAL PRIMARY KEY,
  type TEXT NOT NULL UNIQUE
);

-- 8. Location
CREATE TABLE location (
  id        SERIAL PRIMARY KEY,
  format_id INT REFERENCES format(id),
  info      TEXT
);

-- 9. Time_Slot
CREATE TABLE time_slot (
  id          SERIAL PRIMARY KEY,
  tutor_id    INT  NOT NULL REFERENCES tutor_profiles(id),
  subject_id  INT  NOT NULL REFERENCES subjects(id),
  location_id INT  REFERENCES location(id),
  start_dt    TIMESTAMPTZ NOT NULL,
  end_dt      TIMESTAMPTZ NOT NULL,
  capacity    INT  NOT NULL DEFAULT 1 CHECK (capacity > 0),
  status      TEXT
);

-- 10. Booking
CREATE TYPE booking_status AS ENUM ('booked', 'cancelled', 'completed');

CREATE TABLE booking (
  id         SERIAL PRIMARY KEY,
  slot_id    INT NOT NULL REFERENCES time_slot(id),
  student_id INT NOT NULL REFERENCES student_profiles(id),
  booked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
  status booking_status NOT NULL 
);

-- 11. Review
CREATE TABLE review (
  id              SERIAL PRIMARY KEY,
  student_id      INT NOT NULL REFERENCES student_profiles(id),
  tutorsubject_id INT REFERENCES tutor_subjects(id),
  rating          INT CHECK (rating BETWEEN 1 AND 5),
  comment         TEXT,
  booking_id      INT REFERENCES booking(id)
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
  SELECT start_dt INTO s
  FROM time_slot
  WHERE id = NEW.slot_id;

  IF s IS NULL THEN
    RAISE EXCEPTION 'Slot % not found (booking before start check)', NEW.slot_id;
  END IF;

  IF s <= now() THEN
    RAISE EXCEPTION 'Cannot book slot %: it already started at %', NEW.slot_id, s;
  END IF;

  RETURN NEW;
END;
$$;

-- повесить триггер на таблицу
CREATE TRIGGER trg_booking_before_start
BEFORE INSERT OR UPDATE OF slot_id
ON booking
FOR EACH ROW
EXECUTE FUNCTION check_booking_before_start();

Примеры
select * from time_slot;

1)INSERT INTO booking(slot_id, student_id, status)
VALUES (5, 2, 'booked');

2)INSERT INTO booking(slot_id, student_id, status)
VALUES ( 8, 3,'booked');

-- Удалить
DROP FUNCTION IF EXISTS check_booking_before_start();

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
  -- проверяем только новые или изменённые брони со статусом "booked"
  IF NOT (NEW.status IS NULL OR lower(NEW.status) = 'booked') THEN
    RETURN NEW;
  END IF;

  -- если статус не меняется (уже был 'booked')
  IF TG_OP = 'UPDATE' AND (OLD.status IS NOT NULL AND lower(OLD.status) = 'booked') THEN
    RETURN NEW;
  END IF;

  -- блокируем строку слота (чтобы избежать гонки при одновременных вставках)
  SELECT capacity INTO cap FROM time_slot WHERE id = NEW.slot_id FOR UPDATE;
  IF cap IS NULL THEN
    RAISE EXCEPTION 'Slot % not found (capacity check)', NEW.slot_id;
  END IF;

  -- считаем текущее количество броней со статусом 'booked'
  SELECT count(*) INTO cnt
  FROM booking
  WHERE slot_id = NEW.slot_id AND (status IS NULL OR lower(status) = 'booked');

  -- сравниваем с лимитом
  IF cnt >= cap THEN
    RAISE EXCEPTION 'Cannot book slot %: capacity % already reached', NEW.slot_id, cap;
  END IF;

  RETURN NEW;
END;
$$;

--повесить триггер на таблицу
CREATE TRIGGER trg_booking_capacity
BEFORE INSERT OR UPDATE OF slot_id, status
ON booking
FOR EACH ROW
EXECUTE FUNCTION fn_check_capacity();

-- Примеры
Сколько мест уже занято в слоте 1
SELECT COUNT(*) AS booked_cnt
FROM booking
WHERE slot_id = 1 AND (status IS NULL OR lower(status)='booked');


INSERT INTO booking(slot_id, student_id, status)
VALUES
(1, 1, 'booked');

-- Удалить
DROP FUNCTION IF EXISTS fn_check_capacity();

```

#### 3️⃣ Автоматическая отмена бронирования при отмене слота
```sql
-- Функция: авто-отмена бронирований при переводе слота в status = 'cancelled'
CREATE OR REPLACE FUNCTION fn_auto_cancel_bookings_on_slot_cancel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'UPDATE'
     AND OLD.status IS DISTINCT FROM NEW.status
     AND NEW.status IS NOT NULL
     AND lower(NEW.status) = 'cancelled' THEN

    UPDATE booking
    SET status = 'cancelled',
        updated_at = now()
    WHERE slot_id = NEW.id
      AND (status IS NULL OR lower(status) <> 'cancelled');
  END IF;

  RETURN NEW;
END;
$$;

-- триггер на таблицу time_slot
CREATE TRIGGER trg_slot_auto_cancel
AFTER UPDATE OF status
ON time_slot
FOR EACH ROW
EXECUTE FUNCTION fn_auto_cancel_bookings_on_slot_cancel();

Пример
-- подставь id слота, у которого есть брони
SELECT slot_id, id AS booking_id, status
FROM booking
WHERE slot_id = 1    
ORDER BY id;

Отменить слот
UPDATE time_slot
SET status = 'cancelled'
WHERE id = 1;

Проверить, что брони тоже отменились
SELECT slot_id, id AS booking_id, status
FROM booking
WHERE slot_id = 1
ORDER BY id;

-- Удалить
DROP FUNCTION IF EXISTS fn_auto_cancel_bookings_on_slot_cancel();

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
INSERT INTO roles(name) VALUES
  ('admin'), ('tutor'), ('student');

-- Добавление пользователей
INSERT INTO users(full_name, email, password, phone, role_id) VALUES
  ('Alice',  'alice.tutor@example.com',  'hash', '+9924567895', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Bob',    'bob.tutor@example.com',    'hash', '+9924567235', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Carol',  'carol.tutor@example.com',  'hash', '+9924560487', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Dave',   'dave.student@example.com', 'hash', '+9924564422', (SELECT role_id FROM roles WHERE name='student')),
  ('Eva',    'eva.student@example.com',  'hash', '+9924567332', (SELECT role_id FROM roles WHERE name='student')),
  ('Fred',   'fred.student@example.com', 'hash', '+9924560998', (SELECT role_id FROM roles WHERE name='student')),
  ('Olivia', 'olivia.tutor@example.com', 'hash', '+9924500101', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Peter',  'peter.tutor@example.com',  'hash', '+9924500102', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Quinn',  'quinn.tutor@example.com',  'hash', '+9924500103', (SELECT role_id FROM roles WHERE name='tutor')),
  ('Sara',   'sara.student@example.com', 'hash', '+9924500104', (SELECT role_id FROM roles WHERE name='student')),
  ('Tom',    'tom.student@example.com',  'hash', '+9924500105', (SELECT role_id FROM roles WHERE name='student')),
  ('Uma',    'uma.student@example.com',  'hash', '+9924500106', (SELECT role_id FROM roles WHERE name='student'));

-- 3) Профили репетиторов 
INSERT INTO tutor_profiles(user_id, experience_years, info, rating_count, languages) VALUES
  ((SELECT id FROM users WHERE email='alice.tutor@example.com'), 5, 'Experienced tutor', 0, 'en,ru'),
  ((SELECT id FROM users WHERE email='bob.tutor@example.com'),   3, 'STEM focus',        0, 'en'),
  ((SELECT id FROM users WHERE email='carol.tutor@example.com'), 7, 'IELTS/TOEFL',       0, 'en'),
  ((SELECT id FROM users WHERE email='olivia.tutor@example.com'),4, 'STEM tutor',        0, 'en'),
  ((SELECT id FROM users WHERE email='peter.tutor@example.com'), 6, 'Physics & lab',     0, 'en'),
  ((SELECT id FROM users WHERE email='quinn.tutor@example.com'), 2, 'English speaking',  0, 'en,ru');

-- Профили студентов
INSERT INTO student_profiles(user_id, preferred_language, goals, age) VALUES
  ((SELECT id FROM users WHERE email='dave.student@example.com'), 'en', 'Improve math skills', 21),
  ((SELECT id FROM users WHERE email='eva.student@example.com'),  'en', 'Exam prep',           20),
  ((SELECT id FROM users WHERE email='fred.student@example.com'), 'en', 'Conversation',        22),
  ((SELECT id FROM users WHERE email='sara.student@example.com'), 'en', 'Catch up in math',    19),
  ((SELECT id FROM users WHERE email='tom.student@example.com'),  'en', 'Physics practice',    20),
  ((SELECT id FROM users WHERE email='uma.student@example.com'),  'en', 'Improve speaking',    21);


-- Предметы
INSERT INTO subjects(name) VALUES
  ('Math'), ('Physics'), ('English');

-- Репетиторы и их предметы
INSERT INTO tutor_subjects(subject_id, tutor_id, info, count_students, languages) VALUES
  ((SELECT id FROM subjects WHERE name='Math'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='alice.tutor@example.com')),
   'Teaches with practice', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='English'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='alice.tutor@example.com')),
   'Speaking club', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='Physics'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='bob.tutor@example.com')),
   'Problem solving', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='English'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='carol.tutor@example.com')),
   'Exam focus', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='Math'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='olivia.tutor@example.com')),
   'Algebra & practice', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='Physics'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='peter.tutor@example.com')),
   'Mechanics basics', 0, 'en'),
  ((SELECT id FROM subjects WHERE name='English'),
   (SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='quinn.tutor@example.com')),
   'Speaking club', 0, 'en,ru');

-- Форматы занятий
INSERT INTO format(type) VALUES ('online'), ('offline');

-- Локации
INSERT INTO location(format_id, info) VALUES
  ((SELECT id FROM format WHERE type='online'),  'Zoom'),
  ((SELECT id FROM format WHERE type='offline'), 'Campus Room 101');

-- Тайм-слоты (доступные занятия)
INSERT INTO time_slot(tutor_id, subject_id, location_id, start_dt, end_dt, capacity, status) VALUES
  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='alice.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='Math'),
   (SELECT id FROM location WHERE info='Zoom'),
   '2025-11-12T09:00:00+00'::timestamptz, '2025-11-12T10:00:00+00'::timestamptz, 3, 'published'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='alice.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='Math'),
   (SELECT id FROM location WHERE info='Zoom'),
   '2025-11-13T11:00:00+00'::timestamptz, '2025-11-13T12:00:00+00'::timestamptz, 2, 'draft'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='bob.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='Physics'),
   (SELECT id FROM location WHERE info='Campus Room 101'),
   '2025-11-12T14:00:00+00'::timestamptz, '2025-11-12T15:30:00+00'::timestamptz, 2, 'published'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='carol.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='English'),
   (SELECT id FROM location WHERE info='Zoom'),
   '2025-11-14T08:30:00+00'::timestamptz, '2025-11-14T09:30:00+00'::timestamptz, 1, 'published'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='olivia.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='Math'),
   (SELECT id FROM location WHERE info='Zoom'),
   '2025-11-25T10:00:00+00'::timestamptz, '2025-11-25T11:00:00+00'::timestamptz, 3, 'published'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='peter.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='Physics'),
   (SELECT id FROM location WHERE info='Campus Room 101'),
   '2025-11-26T12:00:00+00'::timestamptz, '2025-11-26T13:30:00+00'::timestamptz, 2, 'published'),

  ((SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='quinn.tutor@example.com')),
   (SELECT id FROM subjects WHERE name='English'),
   (SELECT id FROM location WHERE info='Zoom'),
   '2025-11-27T15:00:00+00'::timestamptz, '2025-11-27T16:00:00+00'::timestamptz, 2, 'published');

-- Бронирование занятий
INSERT INTO booking(slot_id, student_id, status, booked_at) VALUES
  ((SELECT id FROM time_slot WHERE start_dt='2025-11-12T09:00:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='dave.student@example.com')),
   'booked', now() - interval '2 days'),

  ((SELECT id FROM time_slot WHERE start_dt='2025-11-12T14:00:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='eva.student@example.com')),
   'booked', now() - interval '1 day'),

  ((SELECT id FROM time_slot WHERE start_dt='2025-11-14T08:30:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='fred.student@example.com')),
   'cancelled', now()),

  ((SELECT id FROM time_slot WHERE start_dt='2025-11-25T10:00:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='sara.student@example.com')),
   'booked', now() - interval '3 hours'),

  ((SELECT id FROM time_slot WHERE start_dt='2025-11-26T12:00:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='tom.student@example.com')),
   'booked', now() - interval '2 hours'),

  ((SELECT id FROM time_slot WHERE start_dt='2025-11-27T15:00:00+00'::timestamptz),
   (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='uma.student@example.com')),
   'booked', now() - interval '1 hour');

-- Отзывы студентов
INSERT INTO review(student_id, tutorsubject_id, rating, comment, booking_id) VALUES
  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='dave.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='alice.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='Math')),
    5, 'Great session!',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-12T09:00:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='dave.student@example.com')))
  ),

  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='eva.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='bob.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='Physics')),
    4, 'Very helpful',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-12T14:00:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='eva.student@example.com')))
  ),

  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='fred.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='carol.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='English')),
    3, 'Good, but could be longer',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-14T08:30:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='fred.student@example.com')))
  ),

  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='sara.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='olivia.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='Math')),
    5, 'Very helpful, thanks!',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-25T10:00:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='sara.student@example.com')))
  ),

  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='tom.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='peter.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='Physics')),
    4, 'Good explanations',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-26T12:00:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='tom.student@example.com')))
  ),

  (
    (SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='uma.student@example.com')),
    (SELECT id FROM tutor_subjects
       WHERE tutor_id=(SELECT id FROM tutor_profiles WHERE user_id=(SELECT id FROM users WHERE email='quinn.tutor@example.com'))
         AND subject_id=(SELECT id FROM subjects WHERE name='English')),
    5, 'Great speaking practice!',
    (SELECT id FROM booking
       WHERE slot_id=(SELECT id FROM time_slot WHERE start_dt='2025-11-27T15:00:00+00'::timestamptz)
         AND student_id=(SELECT id FROM student_profiles WHERE user_id=(SELECT id FROM users WHERE email='uma.student@example.com')))
  );

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
  SELECT
         ts.id,
         ts.tutor_id,
         ts.subject_id,
         ts.start_dt::text AS start_dt,
         ts.end_dt::text   AS end_dt,
         ts.capacity,
         COALESCE(bc.cnt, 0) AS booked_count,
         ts.capacity - COALESCE(bc.cnt, 0) AS free_places
  FROM time_slot ts
  LEFT JOIN (
    SELECT slot_id, count(*) AS cnt
    FROM booking
    WHERE (status IS NULL OR lower(status) = 'booked')
    GROUP BY slot_id
  ) bc ON bc.slot_id = ts.id
  WHERE ts.start_dt >= p_from
    AND ts.start_dt < p_to
    AND ts.capacity - COALESCE(bc.cnt, 0) > 0
  ORDER BY ts.start_dt;
$$;

Примеры
-- все доступные слоты за неделю
SELECT * FROM fn_get_available_slots('2025-11-20+00', '2025-11-28+00');

-- только по предмету Math
SELECT *
FROM fn_get_available_slots('2025-11-20+00','2025-11-28+00')
WHERE subject_id = (SELECT id FROM subjects WHERE name='Math');

-- Удалить
DROP FUNCTION IF EXISTS fn_get_available_slots(timestamptz, timestamptz);

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
  v_tutor_subject_id INT;
  v_new_id INT;
  v_cnt INT;
  v_avg NUMERIC;
BEGIN
  -- Проверяем существование брони
  SELECT * INTO v_booking FROM booking WHERE id = p_booking_id LIMIT 1;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Booking % not found', p_booking_id;
  END IF;

  -- Проверяем диапазон рейтинга
  IF p_rating IS NULL OR p_rating < 1 OR p_rating > 5 THEN
    RAISE EXCEPTION 'Rating must be between 1 and 5 (got: %)', p_rating;
  END IF;

  -- Находим ID профиля репетитора и связку предмета
  SELECT ts.tutor_id, ts.subject_id INTO v_tutor_profile_id, v_tutor_subject_id
  FROM booking b
  JOIN time_slot ts ON b.slot_id = ts.id
  WHERE b.id = p_booking_id
  LIMIT 1;

  -- Вставляем отзыв
  INSERT INTO review (student_id, tutorsubject_id, booking_id, rating, comment, created_at)
  VALUES (
    v_booking.student_id,
    v_tutor_subject_id,
    p_booking_id,
    p_rating,
    p_comment,
    now()
  )
  RETURNING id INTO v_new_id;

  -- Пересчёт среднего рейтинга
  IF v_tutor_profile_id IS NOT NULL THEN
    SELECT COUNT(r.id), AVG(r.rating)::numeric(3,2)
      INTO v_cnt, v_avg
    FROM review r
    JOIN booking b2 ON r.booking_id = b2.id
    JOIN time_slot ts2 ON b2.slot_id = ts2.id
    WHERE ts2.tutor_id = v_tutor_profile_id
      AND r.rating IS NOT NULL;

    -- Обновляем профиль репетитора
    UPDATE tutor_profiles
    SET rating_count = v_cnt,
        info = CONCAT(info, ' | avg=', ROUND(v_avg, 2)),  -- если нет поля rating_avg
        updated_at = now()
    WHERE id = v_tutor_profile_id;
  END IF;

  RETURN v_new_id;
END;
$$;

-- Добавить отзыв
SELECT fn_add_review(3, 5, 'Отличное занятие!');
SELECT fn_add_review(4, 4, 'Хорошее занятие!');

-- Проверить результат
SELECT * FROM review ORDER BY id DESC;
SELECT id, rating_count, info FROM tutor_profiles;

-- Удалить
DROP FUNCTION IF EXISTS fn_add_review(integer, integer, text);

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

#### 1️⃣ Индекс создан для ускорения выборок ближайших слотов конкретного репетитора по колонкам tutor_id и start_dt. 
 ```
До создания индекса:

EXPLAIN ANALYZE
SELECT id, start_dt
FROM time_slot
WHERE tutor_id = (
  SELECT tp.id
  FROM tutor_profiles tp
  JOIN users u ON u.id = tp.user_id
  WHERE u.email = 'alice.tutor@example.com'
)
AND start_dt >= now()
ORDER BY start_dt;

 Planning Time: 0.884 ms
 Execution Time: 0.186 ms
```
 #### После создания индекса:
 ```
EXPLAIN (ANALYZE, BUFFERS)
SELECT ts.id, ts.start_dt
FROM time_slot ts
JOIN tutor_profiles tp ON tp.id = ts.tutor_id
JOIN users u ON u.id = tp.user_id
WHERE u.email = 'alice.tutor@example.com'
  AND ts.start_dt >= now()
ORDER BY ts.start_dt
LIMIT 20;

 Planning Time: 0.538 ms
 Execution Time: 0.128 ms
```

#### 2️⃣ Индекс для быстрого поиска и просмотра истории бронирований конкретного студента.
```
До создания индекса:

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

 #### 3️⃣ Индекс ускоряет проверки и выборку отзывов, связанных с конкретной бронью. 
```
 До создания индекса:

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, rating, comment
FROM review
WHERE booking_id = 1;

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

 #### 4️⃣ Индекс для ускорения поиска слотов по предмету в заданном интервале дат
 ```
 До создания индекса:

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

 #### 5️⃣ Индекс для ускорения выборок слотов по статусу (published/cancelled/draft)
 До создания индекса:
```
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tutor_id, subject_id, start_dt
FROM time_slot
WHERE status = 'published'
  AND start_dt >= now()            
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
  AND start_dt >= now()            
ORDER BY start_dt;

 Planning Time: 0.378 ms
 Execution Time: 0.051 ms
```
