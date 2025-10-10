
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

## ER-модель
<img alt=" " src="https://github.com/Ilyos2004/CourseWork/blob/dev/part2/ER%20-%20%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C.png">

## DDL-модель
<img alt=" " src="https://github.com/Ilyos2004/CourseWork/blob/dev/part2/DDL%20-%20%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C.png">

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
CREATE TABLE booking (
  id SERIAL PRIMARY KEY,
  slot_id INT NOT NULL REFERENCES time_slot(id) ON DELETE CASCADE,
  student_id INT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
  booked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT
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

### 1️⃣ Проверка — нельзя бронировать слот, который уже начался

```sql
CREATE OR REPLACE FUNCTION check_booking_before_start()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  s timestamptz;
BEGIN
  -- явно приводим TEXT -> timestamptz (работает, если строка в ISO)
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
### 2️⃣ Проверка вместимости (capacity) при вставке брони
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

  SELECT count(*) INTO cnt FROM booking
    WHERE slot_id = NEW.slot_id AND (status IS NULL OR lower(status) = 'booked');

  IF cnt >= cap THEN
    RAISE EXCEPTION 'Cannot book slot %: capacity % already reached', NEW.slot_id, cap;
  END IF;

  RETURN NEW;
END;
$$;
```

3️⃣ Автоматическая отмена бронирования при отмене слота
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





