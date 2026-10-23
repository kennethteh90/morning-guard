-- Per-day time spent on the morning goal screen (foreground seconds).
alter table public.morning_day_records
    add column if not exists time_on_goal_seconds integer not null default 0;

comment on column public.morning_day_records.time_on_goal_seconds is
    'Seconds with the morning goal screen in the foreground that calendar day.';
