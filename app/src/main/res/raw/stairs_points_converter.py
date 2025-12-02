import json
import os

def parse_building_and_floor(name):
    """從名稱解析建築物和樓層（與 classroom converter 保持一致的簡單規則）"""
    name = name.strip().lower()
    building = ""
    building_id = 0
    floor = 0

    if name.startswith('sea'):
        building = 'sea'
        building_id = 1
    elif name.startswith('seb'):
        building = 'seb'
        building_id = 2
    elif name.startswith('sec'):
        building = 'sec'
        building_id = 3

    # 嘗試提取緊接在前三個字後的數字作為樓層
    if len(name) >= 4 and name[3].isdigit():
        floor = int(name[3])

    return building, building_id, floor


def determine_image_resource(building, floor):
    if floor <= 3 and floor > 0:
        return f"R.drawable.se{floor}"
    else:
        return f"R.drawable.{building}{floor}"


file_path = 'stairs_point.json'
with open(file_path, 'r', encoding='utf-8') as f:
    points = json.load(f)

output_file_path = os.path.join(os.path.dirname(file_path), 'reference_points_stairs_output.txt')

with open(output_file_path, 'w', encoding='utf-8') as output_file:
    for point in points:
        # 跳過空物件或缺欄位
        if not point or 'name' not in point or 'id' not in point:
            continue
        pid = point['id']
        name = point['name']
        x = point.get('x')
        y = point.get('y')

        building, building_id, floor = parse_building_and_floor(name)
        if building_id == 0 or floor == 0:
            # 如果解析失敗，嘗試用 imageId 字串作為 fallback（無法保證正確）
            image_resource = 'R.drawable.se1'
        else:
            image_resource = determine_image_resource(building, floor)

        building_char = chr(64 + building_id) if building_id > 0 else ''
        floor_id = building_id * 10 + floor if building_id > 0 and floor > 0 else 0

        line = f'ReferencePointEntity("{pid}", "{name}", {x}, {y}, {image_resource}, 0, "STAIRS", "{building_char}", {floor_id}),'
        output_file.write(line + '\n')

print(f'輸出已保存到: {os.path.abspath(output_file_path)}')
