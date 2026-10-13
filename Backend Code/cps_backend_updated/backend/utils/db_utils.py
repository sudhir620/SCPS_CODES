from db import get_db, close_db
from typing import Union, Optional
import re

def all_equal_followed_by_placeholder(query: str) -> tuple[bool, int]:
    matches = re.findall(r'= *%s', query)
    equal_count = query.count('=')
    return len(matches) == equal_count, equal_count


def execute_query(query, args=None, conn = None, is_fetchone = False, is_rollback = False):
    try:
        print("query = ", query, ">>>>>>>> args = ", args)
        conn = conn or get_db()
        if not conn.open: 
            conn = get_db()
        with conn.cursor() as cur:
            row_affected = cur.execute(query, args or ())
            result = None    
            if query.strip().upper().startswith("SELECT"):
                result = cur.fetchone() if is_fetchone else cur.fetchall()
            elif query.strip().upper().startswith("INSERT"):
                result = cur.lastrowid
            elif query.strip().upper().startswith("UPDATE"):
                result = row_affected
            if not is_rollback:
                conn.commit()
            return result
    except Exception as e:
        raise Exception(e)
    finally:
        close_db()

def build_query(data: dict, isInsert: bool = True) -> tuple[str, tuple]:
    if not isinstance(data, dict):
        raise TypeError("Data must be a dictionary!")
    keys, args = [], []
    for key, value in data.items():
        if value is not None:
            keys.append(f"`{key}`" if isInsert else f'`{key}`= %s')
            args.append(value)
    if len(keys) == 0 or len(args) == 0:
        raise Exception("Data should not be empty!")
    return  f'({", ".join(keys)}) VALUES ({', '.join(['%s'] * len(keys))})' if isInsert else ", ".join(keys), tuple(args)

def build_query_and_execute(data: dict, isInsert: bool = True, table_name:Optional[str] = None, where_condition:Optional[Union[dict, str]] = None, where_args:Optional[list] = None) -> Optional[int]:
    if table_name is None:
        raise ValueError("Table name is required!")
    if not isInsert and where_condition is None:
        raise ValueError("WHERE condition is required for update queries!")
    placeholders, args = build_query(data=data, isInsert=isInsert)
    if isInsert:
        query = f"INSERT INTO {table_name} {placeholders};"
    else:
        where_cond = ""
        if isinstance(where_condition, str):
            a, b = all_equal_followed_by_placeholder(where_condition)
            if a:
                if b != len(where_args):
                    raise Exception("'where_args' length should be same as count of %s")
                where_cond = where_condition
                if where_args and len(where_args) > 0:
                    args += tuple(where_args)
            else:
                raise Exception("Where condition cannot contain values directly after '=', please use 'where_args' parameter.")
        else:
            where_cond, wargs = build_query(data=where_condition, isInsert=False)
            args += wargs
        query = f"UPDATE {table_name} SET {placeholders} WHERE {where_cond};"
    return execute_query(query=query, args=args)