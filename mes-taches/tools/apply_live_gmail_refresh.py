from pathlib import Path

path = Path("mes-taches/app/src/main/java/fr/manubotelho/mestaches/MailImportActivity.java")
text = path.read_text(encoding="utf-8")

old_list = 'HttpResult listResult=get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15",token);'
new_list = '''HttpResult listResult=get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15&labelIds=INBOX&includeSpamTrash=false",token);'''
if old_list in text:
    text = text.replace(old_list, new_list, 1)

old_get = '''        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setRequestProperty("Accept","application/json, message/rfc822, */*");'''
new_get = '''        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control","no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma","no-cache");
        connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setRequestProperty("Accept","application/json, message/rfc822, */*");'''
if old_get in text:
    text = text.replace(old_get, new_get, 1)

old_status = 'setStatus("Chargement de tes derniers mails Gmail…");'
new_status = 'setStatus("Actualisation de ta boîte Gmail…");'
if old_status in text:
    text = text.replace(old_status, new_status, 1)

path.write_text(text, encoding="utf-8")
