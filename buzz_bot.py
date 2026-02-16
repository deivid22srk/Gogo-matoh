import os
import time
import requests
import urllib.parse
from pyrogram import Client, filters

# --- CONFIGURAÇÕES ---
API_ID = '20690169'        # Obtenha em my.telegram.org
API_HASH = '4222211cf4f8180a88b2b66ffa5420b0'    # Obtenha em my.telegram.org
BOT_TOKEN = '8343442752:AAHZbleu8_exX0Oxt5CuTA4L-E6LMxhxfps'

# Se você tiver uma conta, coloque seu ID aqui. 
# Se deixar vazio, o upload será anônimo.
BUZZ_ACCOUNT_ID = 'A9NP0TR0CXF3RZORB47V' 
# ---------------------

app = Client("buzz_uploader", api_id=API_ID, api_hash=API_HASH, bot_token=BOT_TOKEN)

def create_progress_bar(current, total):
    if total == 0: return "[░░░░░░░░░░] 0%"
    percentage = current * 100 / total
    finished_blocks = int(percentage / 10)
    return f"[{'■' * finished_blocks}{'□' * (10 - finished_blocks)}] {percentage:.1f}%"

async def progress_func(current, total, message, text):
    try:
        now = time.time()
        if not hasattr(progress_func, "last_update"): progress_func.last_update = 0
        
        # Atualiza a cada 4 segundos para evitar spam
        if now - progress_func.last_update > 4 or current == total:
            progress_func.last_update = now
            bar = create_progress_bar(current, total)
            await message.edit_text(f"{text}\n{bar}\n\n📦 {current/1024/1024:.1f}MB / {total/1024/1024:.1f}MB")
    except:
        pass

@app.on_message(filters.command("start"))
async def start(client, message):
    await message.reply_text("🐝 **Buzzheavier Uploader Ativo!**\nEnvie qualquer arquivo de até 2GB.")

@app.on_message(filters.document | filters.video | filters.audio | filters.photo)
async def handle_media(client, message):
    status = await message.reply_text("📥 Baixando do Telegram...")
    file_path = None
    
    try:
        # 1. DOWNLOAD DO TELEGRAM
        file_path = await message.download(
            progress=progress_func,
            progress_args=(status, "📥 Baixando do Telegram:")
        )

        filename = os.path.basename(file_path)
        # Codifica o nome do arquivo para URL (ex: espaços viram %20)
        encoded_name = urllib.parse.quote(filename)
        
        # 2. CONFIGURAÇÃO DO UPLOAD BUZZHEAVIER
        await status.edit_text(f"📤 Enviando para Buzzheavier...\nArquivo: `{filename}`")
        
        url = f"https://w.buzzheavier.com/{encoded_name}"
        headers = {}
        if BUZZ_ACCOUNT_ID:
            headers['Authorization'] = f'Bearer {BUZZ_ACCOUNT_ID}'
        
        # O Buzzheavier usa método PUT para upload de arquivos
        with open(file_path, 'rb') as f:
            # timeout=None é importante para arquivos de GBs
            response = requests.put(url, data=f, headers=headers, timeout=None)

        # 3. PROCESSAR RESPOSTA
        if response.status_code in [200, 201]:
            try:
                result = response.json()
                # O Buzzheavier retorna um JSON com o campo 'url' ou 'link'
                download_link = result.get('url') or f"https://buzzheavier.com/{encoded_name}"
            except:
                # Se não retornar JSON, o link geralmente é o próprio endpoint sem o 'w.'
                download_link = f"https://buzzheavier.com/{encoded_name}"

            await status.edit_text(
                f"✅ **Upload Concluído!**\n\n"
                f"📄 **Arquivo:** `{filename}`\n"
                f"🔗 **Link:** {download_link}",
                disable_web_page_preview=True
            )
        else:
            await status.edit_text(f"❌ Erro no Buzzheavier: {response.status_code}\n{response.text[:100]}")

    except Exception as e:
        await status.edit_text(f"❌ Erro Crítico: {str(e)}")
    finally:
        if file_path and os.path.exists(file_path):
            os.remove(file_path)

print("Bot Buzzheavier iniciado!")
app.run()