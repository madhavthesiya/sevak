-- Runs after every Flyway migrate, so a changed password takes effect on the next start.
-- Lets sevak_app log in with the password the app is configured with. If no password is configured,
-- this does nothing and whoever manages the database sets the login themselves.
--
-- Flyway doesn't replace placeholders inside dollar-quoted text such as a DO block, so the password
-- reaches the block through a setting, which is cleared straight after. It must not contain a quote (').
-- SET, not SELECT set_config(...): Flyway prints the rows a callback's SELECT returns, which put the
-- password in the application log on every start.
SET sevak.app_password = '${app_password}';

DO $do$
BEGIN
    IF current_setting('sevak.app_password') <> '' THEN
        EXECUTE format('ALTER ROLE sevak_app WITH LOGIN PASSWORD %L', current_setting('sevak.app_password'));
    END IF;
END
$do$;

RESET sevak.app_password;
