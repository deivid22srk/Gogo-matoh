import os
import time
import requests
from pyrogram import Client, filters

# --- CONFIGURAÇÕES ---
API_ID = '20690169'        # Obtenha em my.telegram.org
API_HASH = '4222211cf4f8180a88b2b66ffa5420b0'    # Obtenha em my.telegram.org
BOT_TOKEN = '8343442752:AAHZbleu8_exX0Oxt5CuTA4L-E6LMxhxfps'
GOFILE_TOKEN = 'dMdC3Ivd2QGnoD4TX9QGn6NM88PSQb8T'
# ---------------------

app = Client("gofile_bot", api_id=API_ID, api_hash=API_HASH, bot_token=BOT_TOKEN)

def create_progress_bar(current, total):
    percentage = current * 100 / total
    finished_blocks = int(percentage / 10)
    return f"[{'■' * finished_blocks}{'□' * (10 - finished_blocks)}] {percentage:.1f}%"

# Função para atualizar o progresso no Telegram
async def progress_func(current, total, message, text):
    try:
        # Só atualiza a cada 10% ou se terminar para evitar flood
        last_percent = getattr(progress_func, "last_percent", 0)
        curr_percent = int(current * 100 / total)
        
        if curr_percent >= last_percent + 10 or curr_percent == 100:
            progress_func.last_percent = curr_percent
            bar = create_progress_bar(current, total)
            await message.edit_text(f"{text}\n{bar}\n\nTam: {current/1024/1024:.1f}MB / {total/1024/1024:.1f}MB")
    except:
        pass

def get_best_server():
    try:
        res = requests.get("https://api.gofile.io/servers").json()
        return res['data']['servers'][0]['name']
    except:
        return "store1"

@app.on_message(filters.command("start"))
async def start(client, message):
    await message.reply_text("✅ Bot Online! Agora eu suporto arquivos de até 2GB.\nEnvie qualquer arquivo para começar.")

@app.on_message(filters.document | filters.video | filters.audio | filters.photo)
async def handle_media(client, message):
    status = await message.reply_text("📥 Iniciando download do Telegram...")
    file_path = None
    
    try:
        # 1. DOWNLOAD (Até 2GB suportado aqui)
        progress_func.last_percent = 0
        file_path = await message.download(
            progress=progress_func,
            progress_args=(status, "📥 Baixando do Telegram:")
        )

        # 2. SELEÇÃO DE SERVIDOR
        await status.edit_text("🔍 Buscando servidor Gofile...")
        server = get_best_server()
        
        # 3. UPLOAD PARA GOFILE
        await status.edit_text(f"📤 Enviando para Gofile ({server})...\nAguarde, isso depende do tamanho do arquivo.")
        
        url = f"https://{server}.gofile.io/contents/uploadfile"
        with open(file_path, 'rb') as f:
            files = {'file': f}
            data = {'token': GOFILE_TOKEN} if GOFILE_TOKEN else {}
            response = requests.post(url, files=files, data=data)
            result = response.json()

        if result['status'] == 'ok':
            link = result['data']['downloadPage']
            filename = os.path.basename(file_path)
            await status.edit_text(
                f"✅ **Upload Concluído!**\n\n"
                f"📄 **Arquivo:** `{filename}`\n"
                f"🔗 **Link:** {link}",
                disable_web_page_preview=True
            )
        else:
            await status.edit_text(f"❌ Erro Gofile: {result.get('error')}")

    except Exception as e:
        await status.edit_text(f"❌ Erro: {str(e)}")
    finally:
        if file_path and os.path.exists(file_path):
            os.remove(file_path)

print("Bot Iniciado com Pyrogram (Suporte a arquivos grandes)!")
app.run()