import os
import time
import logging
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, ContextTypes, MessageHandler, filters, CommandHandler

# --- CONFIGURAÇÕES ---
TELEGRAM_BOT_TOKEN = '8343442752:AAHZbleu8_exX0Oxt5CuTA4L-E6LMxhxfps'
GOFILE_API_TOKEN = 'dMdC3Ivd2QGnoD4TX9QGn6NM88PSQb8T' 
# ---------------------

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

def create_progress_bar(current, total):
    """Cria uma barra de progresso visual [###-----]"""
    percentage = (current / total) * 100
    finished_blocks = int(percentage / 10)
    remaining_blocks = 10 - finished_blocks
    return f"[{'■' * finished_blocks}{'□' * remaining_blocks}] {percentage:.1f}%"

async def edit_message_with_throttle(message, text, last_update_time):
    """Atualiza a mensagem apenas se passou mais de 2 segundos para evitar ban."""
    current_time = time.time()
    if current_time - last_update_time[0] > 2.5:
        try:
            await message.edit_text(text)
            last_update_time[0] = current_time
        except Exception:
            pass

def get_best_server():
    try:
        res = requests.get("https://api.gofile.io/servers").json()
        return res['data']['servers'][0]['name'] if res['status'] == 'ok' else "store1"
    except:
        return "store1"

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Envie um arquivo e eu mostrarei o progresso do upload!")

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    msg = update.message
    file_obj = msg.document or msg.video or msg.audio or (msg.photo[-1] if msg.photo else None)
    
    if not file_obj: return
    
    file_name = getattr(file_obj, 'file_name', f"file_{file_obj.file_unique_id}")
    status_msg = await msg.reply_text("🚀 Iniciando...")
    last_update = [time.time()]

    try:
        # 1. PEGAR LINK DO TELEGRAM
        tg_file = await context.bot.get_file(file_obj.file_id)
        file_url = tg_file.file_path
        file_path = f"temp_{file_name}"

        # 2. DOWNLOAD COM PROGRESSO
        response = requests.get(file_url, stream=True)
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=1024*1024): # 1MB por vez
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    bar = create_progress_bar(downloaded, total_size)
                    await edit_message_with_throttle(status_msg, f"📥 Baixando do Telegram:\n{bar}", last_update)

        await status_msg.edit_text("🔍 Buscando servidor Gofile...")
        server = get_best_server()
        
        # 3. UPLOAD COM PROGRESSO (Usando um gerador para ler o arquivo)
        await status_msg.edit_text(f"📤 Fazendo upload para {server}...")
        
        class ProgressFile:
            def __init__(self, filename):
                self.filename = filename
                self.total_size = os.path.getsize(filename)
                self.uploaded = 0

            def __iter__(self):
                with open(self.filename, 'rb') as f:
                    while True:
                        chunk = f.read(1024*1024) # 1MB
                        if not chunk: break
                        self.uploaded += len(chunk)
                        # Gambiarra para atualizar o progresso durante o upload
                        import asyncio
                        loop = asyncio.get_event_loop()
                        bar = create_progress_bar(self.uploaded, self.total_size)
                        loop.create_task(edit_message_with_throttle(status_msg, f"📤 Enviando ao Gofile:\n{bar}", last_update))
                        yield chunk

        upload_url = f"https://{server}.gofile.io/contents/uploadfile"
        data = {'token': GOFILE_API_TOKEN} if GOFILE_API_TOKEN else {}
        
        # O requests aceita um gerador no parâmetro 'files' para fazer stream
        with open(file_path, 'rb') as f:
            files = {'file': (file_name, f)}
            # Nota: Para progresso real de upload no 'requests' sem libs extras, 
            # o monitoramento é limitado, mas aqui ele enviará o arquivo.
            response = requests.post(upload_url, files=files, data=data)
            
        result = response.json()

        if result['status'] == 'ok':
            await status_msg.edit_text(f"✅ Concluído!\n\n📄 {file_name}\n🔗 {result['data']['downloadPage']}")
        else:
            await status_msg.edit_text(f"❌ Erro: {result.get('error')}")

    except Exception as e:
        await status_msg.edit_text(f"❌ Erro fatal: {str(e)}")
    finally:
        if 'file_path' in locals() and os.path.exists(file_path):
            os.remove(file_path)

if __name__ == '__main__':
    app = ApplicationBuilder().token(TELEGRAM_BOT_TOKEN).build()
    app.add_handler(CommandHandler('start', start))
    app.add_handler(MessageHandler(filters.ALL & ~filters.COMMAND, handle_document))
    print("Bot rodando...")
    app.run_polling()