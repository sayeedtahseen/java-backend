alter table app_user add constraint ck_app_user_email_lower check (email = lower(email));
