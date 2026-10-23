drop policy if exists "morning_day_records_delete_own" on public.morning_day_records;
create policy "morning_day_records_delete_own"
    on public.morning_day_records
    for delete
    to authenticated
    using (auth.uid() = user_id);
